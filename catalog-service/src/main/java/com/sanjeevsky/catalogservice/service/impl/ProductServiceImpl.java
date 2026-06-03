package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.service.BrandService;
import com.sanjeevsky.catalogservice.service.CategoryService;
import com.sanjeevsky.catalogservice.service.ProductService;
import com.sanjeevsky.catalogservice.service.SubCategoryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.ErrorConstants.NO_PRODUCT_FOUND_WITH_GIVEN_UUID;
import static com.sanjeevsky.catalogservice.utils.ErrorConstants.PRODUCT_WITH_THIS_MODEL_AND_BRAND_ALREADY_EXISTS_IN_CATALOG;

@Service
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> ALLOWED_PRODUCT_SORT_FIELDS = Arrays.asList(
            "name", "createdAt", "modifiedAt", "mrpPrice", "salePrice");

    private final ProductRepository productRepository;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final SubCategoryService subCategoryService;

    public ProductServiceImpl(
            ProductRepository productRepository,
            BrandService brandService,
            CategoryService categoryService,
            SubCategoryService subCategoryService) {
        this.productRepository = productRepository;
        this.brandService = brandService;
        this.categoryService = categoryService;
        this.subCategoryService = subCategoryService;
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public Product addProduct(UUID brandId, UUID categoryId, UUID subCategoryId, Product product){
        validateProductRequest(product);
        if (productRepository.findByModelAndBrandId(product.getModel(), brandId).isPresent()) {
            throw new ProductAlreadyExistsException(PRODUCT_WITH_THIS_MODEL_AND_BRAND_ALREADY_EXISTS_IN_CATALOG);
        } else {
            //Generate Product from Request Data
            product.setBrand(brandService.getBrand(brandId));
            product.setCategory(categoryService.getCategory(categoryId));
            product.setSubCategory(subCategoryService.getSubCategory(subCategoryId));
            return productRepository.save(product);
        }
    }

    @Override
    @Cacheable(value = "products", key = "#uuid")
    public Product getProduct(UUID uuid){
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
    public Page<Product> searchProducts(String keyword, UUID categoryId, UUID brandId, int page, int size) {
        validatePagination(page, size);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        String kw = (keyword != null && !keyword.isBlank()) ? keyword : null;
        return productRepository.search(kw, categoryId, brandId, pageable);
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

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidProductRequestException("Page index must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidProductRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
