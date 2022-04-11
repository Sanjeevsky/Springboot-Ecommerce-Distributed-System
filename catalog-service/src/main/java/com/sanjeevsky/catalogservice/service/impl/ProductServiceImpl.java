package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.service.BrandService;
import com.sanjeevsky.catalogservice.service.CategoryService;
import com.sanjeevsky.catalogservice.service.ProductService;
import com.sanjeevsky.catalogservice.service.SubCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public Product addProduct(UUID brandId, UUID categoryId, UUID subCategoryId, Product product) throws ProductAlreadyExistsException, BrandNotExistsException, CategoryNotExistsException, SubCategoryNotExistsException, SubCategoryListEmptyException {
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
    public Product getProduct(UUID uuid) throws NoSuchProductExistsException {
        Optional<Product> product = productRepository.findById(uuid);
        if (product.isEmpty()) {
            throw new NoSuchProductExistsException(NO_PRODUCT_FOUND_WITH_GIVEN_UUID);
        }
        return product.get();
    }
}
