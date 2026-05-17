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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

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
        String kw = (keyword != null && !keyword.isBlank()) ? keyword : null;
        return productRepository.search(kw, categoryId, brandId, pageable);
    }
}
