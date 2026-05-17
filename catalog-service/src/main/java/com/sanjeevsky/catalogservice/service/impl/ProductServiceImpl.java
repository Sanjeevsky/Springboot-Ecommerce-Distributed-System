package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.service.BrandService;
import com.sanjeevsky.catalogservice.service.CategoryService;
import com.sanjeevsky.catalogservice.service.ProductService;
import com.sanjeevsky.catalogservice.service.SubCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.sanjeevsky.catalogservice.utils.ErrorConstants.NO_PRODUCT_FOUND_WITH_GIVEN_UUID;
import static com.sanjeevsky.catalogservice.utils.ErrorConstants.PRODUCT_WITH_THIS_MODEL_AND_BRAND_ALREADY_EXISTS_IN_CATALOG;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private BrandService brandService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private SubCategoryService subCategoryService;

    @Override
    public Product addProduct(UUID brandId, UUID categoryId, UUID subCategoryId, Product product){
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
    public Product getProduct(UUID uuid){
        Optional<Product> product = productRepository.findById(uuid);
        if (product.isEmpty()) {
            throw new NoSuchProductExistsException(NO_PRODUCT_FOUND_WITH_GIVEN_UUID);
        }
        return product.get();
    }

    @Override
    public Page<Product> listProducts(int page, int size, String sort) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sort).ascending());
        return productRepository.findAllByStatus(1, pageable);
    }

    @Override
    public Page<Product> searchProducts(String keyword, UUID categoryId, UUID brandId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());

        boolean hasKeyword = keyword != null && !keyword.isEmpty();
        boolean hasCategoryId = categoryId != null;
        boolean hasBrandId = brandId != null;

        if (hasKeyword && hasCategoryId && hasBrandId) {
            // keyword + category + brand: fetch by name, filter category and brand in memory (MVP)
            Page<Product> byName = productRepository.findByNameContainingIgnoreCaseAndStatus(keyword, 1, pageable);
            List<Product> filtered = byName.getContent().stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(categoryId))
                    .filter(p -> p.getBrand() != null && p.getBrand().getId().equals(brandId))
                    .collect(Collectors.toList());
            return new PageImpl<>(filtered, pageable, filtered.size());
        } else if (hasKeyword && hasCategoryId) {
            // keyword + category: fetch by name, filter category in memory
            Page<Product> byName = productRepository.findByNameContainingIgnoreCaseAndStatus(keyword, 1, pageable);
            List<Product> filtered = byName.getContent().stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(categoryId))
                    .collect(Collectors.toList());
            return new PageImpl<>(filtered, pageable, filtered.size());
        } else if (hasKeyword && hasBrandId) {
            // keyword + brand: fetch by name, filter brand in memory
            Page<Product> byName = productRepository.findByNameContainingIgnoreCaseAndStatus(keyword, 1, pageable);
            List<Product> filtered = byName.getContent().stream()
                    .filter(p -> p.getBrand() != null && p.getBrand().getId().equals(brandId))
                    .collect(Collectors.toList());
            return new PageImpl<>(filtered, pageable, filtered.size());
        } else if (hasCategoryId && hasBrandId) {
            return productRepository.findByCategoryIdAndBrandIdAndStatus(categoryId, brandId, 1, pageable);
        } else if (hasKeyword) {
            return productRepository.findByNameContainingIgnoreCaseAndStatus(keyword, 1, pageable);
        } else if (hasCategoryId) {
            return productRepository.findByCategoryIdAndStatus(categoryId, 1, pageable);
        } else if (hasBrandId) {
            return productRepository.findByBrandIdAndStatus(brandId, 1, pageable);
        } else {
            return productRepository.findAllByStatus(1, pageable);
        }
    }
}
