package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.dto.ProductUpdateRequest;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.search.ProductDocumentMapper;
import com.sanjeevsky.catalogservice.search.document.ProductDocument;
import com.sanjeevsky.catalogservice.search.repository.ProductSearchRepository;
import com.sanjeevsky.catalogservice.service.BrandService;
import com.sanjeevsky.catalogservice.service.CategoryService;
import com.sanjeevsky.catalogservice.service.ProductService;
import com.sanjeevsky.catalogservice.service.SubCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.sanjeevsky.catalogservice.utils.ErrorConstants.NO_PRODUCT_FOUND_WITH_GIVEN_UUID;
import static com.sanjeevsky.catalogservice.utils.ErrorConstants.PRODUCT_WITH_THIS_MODEL_AND_BRAND_ALREADY_EXISTS_IN_CATALOG;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> ALLOWED_PRODUCT_SORT_FIELDS = Arrays.asList(
            "name", "createdAt", "modifiedAt", "mrpPrice", "salePrice");

    private final ProductRepository productRepository;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final SubCategoryService subCategoryService;
    private final ProductSearchRepository searchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductDocumentMapper documentMapper;

    public ProductServiceImpl(
            ProductRepository productRepository,
            BrandService brandService,
            CategoryService categoryService,
            SubCategoryService subCategoryService,
            ProductSearchRepository searchRepository,
            ElasticsearchOperations elasticsearchOperations,
            ProductDocumentMapper documentMapper) {
        this.productRepository = productRepository;
        this.brandService = brandService;
        this.categoryService = categoryService;
        this.subCategoryService = subCategoryService;
        this.searchRepository = searchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.documentMapper = documentMapper;
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public Product addProduct(UUID brandId, UUID categoryId, UUID subCategoryId, Product product) {
        validateCatalogIds(brandId, categoryId, subCategoryId);
        validateProductRequest(product);
        if (productRepository.findByModelAndBrandId(product.getModel(), brandId).isPresent()) {
            throw new ProductAlreadyExistsException(PRODUCT_WITH_THIS_MODEL_AND_BRAND_ALREADY_EXISTS_IN_CATALOG);
        }
        product.setBrand(brandService.getBrand(brandId));
        product.setCategory(categoryService.getCategory(categoryId));
        product.setSubCategory(subCategoryService.getSubCategory(subCategoryId));
        Product saved = productRepository.save(product);
        indexProduct(saved);
        return saved;
    }

    @Override
    @Cacheable(value = "products", key = "#uuid")
    public Product getProduct(UUID uuid) {
        if (uuid == null) {
            throw new InvalidProductRequestException("Product id is required");
        }
        Optional<Product> product = productRepository.findById(uuid);
        if (product.isEmpty()) {
            throw new NoSuchProductExistsException(NO_PRODUCT_FOUND_WITH_GIVEN_UUID);
        }
        return product.get();
    }

    @Override
    public Page<Product> listProducts(int page, int size, String sort) {
        validatePagination(page, size);
        String sortField = normalizeProductSort(sort);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sortField).ascending());
        return productRepository.findAllByStatus(1, pageable);
    }

    @Override
    public Page<Product> listProductsForAdmin(
            String keyword,
            Integer status,
            int page,
            int size,
            String sort) {
        validatePagination(page, size);
        validateStatus(status);
        String sortField = normalizeProductSort(sort);
        String normalizedKeyword = isBlank(keyword) ? null : keyword.trim();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sortField).descending());
        return productRepository.searchForAdmin(normalizedKeyword, status, pageable);
    }

    @Override
    public Page<Product> searchProducts(String keyword, UUID categoryId, UUID brandId, int page, int size) {
        validatePagination(page, size);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        String kw = (keyword != null && !keyword.isBlank()) ? keyword : null;
        try {
            return searchViaOpenSearch(kw, categoryId, brandId, pageable);
        } catch (Exception e) {
            log.warn("OpenSearch unavailable, falling back to MySQL search: {}", e.getMessage());
            return productRepository.search(kw, categoryId, brandId, pageable);
        }
    }

    private Page<Product> searchViaOpenSearch(String kw, UUID categoryId, UUID brandId, PageRequest pageable) {
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery("status", 1));

        if (kw != null) {
            bool.must(QueryBuilders.multiMatchQuery(kw, "name", "description", "brand", "categoryName")
                    .fuzziness(Fuzziness.AUTO));
        }
        if (categoryId != null) {
            bool.filter(QueryBuilders.termQuery("categoryId", categoryId.toString()));
        }
        if (brandId != null) {
            bool.filter(QueryBuilders.termQuery("brandId", brandId.toString()));
        }

        // ES sorts by relevance score — don't pass JPA sort (Text fields reject fielddata sort)
        PageRequest esPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        SearchHits<ProductDocument> hits = elasticsearchOperations.search(
                new NativeSearchQueryBuilder().withQuery(bool).withPageable(esPageable).build(),
                ProductDocument.class);

        List<UUID> ids = hits.getSearchHits().stream()
                .map(h -> UUID.fromString(h.getContent().getId()))
                .collect(Collectors.toList());

        if (ids.isEmpty()) return new PageImpl<>(Collections.emptyList(), pageable, 0);
        List<Product> products = productRepository.findAllById(ids);
        return new PageImpl<>(products, pageable, hits.getTotalHits());
    }

    @Override
    public List<String> suggestProducts(String prefix, int size) {
        if (prefix == null || prefix.isBlank()) return Collections.emptyList();
        int cap = (size < 1 || size > 20) ? 5 : size;
        try {
            SearchHits<ProductDocument> hits = elasticsearchOperations.search(
                    new NativeSearchQueryBuilder()
                            .withQuery(QueryBuilders.matchPhrasePrefixQuery("name", prefix).maxExpansions(10))
                            .withPageable(PageRequest.of(0, cap))
                            .build(),
                    ProductDocument.class);
            return hits.getSearchHits().stream()
                    .map(h -> h.getContent().getName())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("ES suggest unavailable: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public Product updateProduct(UUID productId, ProductUpdateRequest request) {
        if (productId == null) {
            throw new InvalidProductRequestException("Product id is required");
        }
        if (request == null) {
            throw new InvalidProductRequestException("Product update request is required");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NoSuchProductExistsException(NO_PRODUCT_FOUND_WITH_GIVEN_UUID));

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription().trim());
        if (request.getModel() != null) product.setModel(request.getModel());
        if (request.getMrpPrice() != null) product.setMrpPrice(request.getMrpPrice());
        if (request.getSalePrice() != null) product.setSalePrice(request.getSalePrice());
        if (request.getGstValue() != null) product.setGstValue(request.getGstValue());
        if (request.getDiscount() != null) product.setDiscount(request.getDiscount());
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        if (request.getHasVariant() != null) product.setHasVariant(request.getHasVariant());
        if (request.getImages() != null) product.setImages(request.getImages());
        if (request.getBrandId() != null) product.setBrand(brandService.getBrand(request.getBrandId()));
        if (request.getCategoryId() != null) product.setCategory(categoryService.getCategory(request.getCategoryId()));
        if (request.getSubCategoryId() != null) {
            product.setSubCategory(subCategoryService.getSubCategory(request.getSubCategoryId()));
        }

        validateProductRequest(product);
        UUID brandId = product.getBrand() == null ? null : product.getBrand().getId();
        if (brandId == null) {
            throw new InvalidProductRequestException("Brand id is required");
        }
        Optional<Product> duplicate = productRepository.findByModelAndBrandId(product.getModel(), brandId);
        if (duplicate.isPresent() && !productId.equals(duplicate.get().getId())) {
            throw new ProductAlreadyExistsException(PRODUCT_WITH_THIS_MODEL_AND_BRAND_ALREADY_EXISTS_IN_CATALOG);
        }

        Product saved = productRepository.save(product);
        indexProduct(saved);
        return saved;
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public Product retireProduct(UUID productId) {
        if (productId == null) {
            throw new InvalidProductRequestException("Product id is required");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NoSuchProductExistsException(NO_PRODUCT_FOUND_WITH_GIVEN_UUID));
        product.setStatus(0);
        Product saved = productRepository.save(product);
        indexProduct(saved);
        return saved;
    }

    private void indexProduct(Product product) {
        try {
            searchRepository.save(documentMapper.toDocument(product));
        } catch (Exception e) {
            log.warn("OpenSearch index skipped for product {}: {}", product.getId(), e.getMessage());
        }
    }

    private void validateProductRequest(Product product) {
        if (product == null) {
            throw new InvalidProductRequestException("Product request is required");
        }
        if (isBlank(product.getName())) {
            throw new InvalidProductRequestException("Product name is required");
        }
        if (isBlank(product.getModel())) {
            throw new InvalidProductRequestException("Product model is required");
        }
        if (product.getMrpPrice() <= 0) {
            throw new InvalidProductRequestException("MRP price must be positive");
        }
        if (product.getSalePrice() <= 0) {
            throw new InvalidProductRequestException("Sale price must be positive");
        }
        if (product.getSalePrice() > product.getMrpPrice()) {
            throw new InvalidProductRequestException("Sale price cannot exceed MRP price");
        }
        if (product.getGstValue() < 0) {
            throw new InvalidProductRequestException("GST value must not be negative");
        }
        if (product.getDiscount() < 0) {
            throw new InvalidProductRequestException("Discount must not be negative");
        }
        if (product.getStatus() != 0 && product.getStatus() != 1) {
            throw new InvalidProductRequestException("Status must be 0 or 1");
        }
        product.setName(product.getName().trim());
        product.setModel(product.getModel().trim());
    }

    private void validateCatalogIds(UUID brandId, UUID categoryId, UUID subCategoryId) {
        if (brandId == null) throw new InvalidProductRequestException("Brand id is required");
        if (categoryId == null) throw new InvalidProductRequestException("Category id is required");
        if (subCategoryId == null) throw new InvalidProductRequestException("Subcategory id is required");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeProductSort(String sort) {
        String sortField = isBlank(sort) ? "name" : sort.trim();
        if (!ALLOWED_PRODUCT_SORT_FIELDS.contains(sortField)) {
            throw new InvalidProductRequestException("Product sort must be one of: "
                    + String.join(", ", ALLOWED_PRODUCT_SORT_FIELDS));
        }
        return sortField;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new InvalidProductRequestException("Status must be 0 or 1");
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0) throw new InvalidProductRequestException("Page index must not be negative");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidProductRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
