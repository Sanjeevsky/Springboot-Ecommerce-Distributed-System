package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.request.ProductRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl implements ProductService {

    public static final String PRODUCT_WITH_THIS_MODEL_AND_BRAND_ALREADY_EXISTS_IN_CATALOG = "Product with this Model and Brand Already Exists in Catalog...!!!";
    public static final String BRAND_DOES_NOT_EXISTS = "Brand With Given Id Doesn't Exists...!!";
    public static final String CATEGORY_DOES_NOT_EXISTS = "Category with Given Id Does not Exists ..!!";
    public static final String SUB_CATEGORY_DOES_NOT_EXISTS = "Sub-Category with Given Id Does not Exists ..!!";
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private BrandService brandService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private SubCategoryService subCategoryService;

    @Override
    public Product addProduct(ProductRequest productRequest) throws ProductAlreadyExistsException, BrandNotExistsException, CategoryNotExistsException, SubCategoryNotExistsException, SubCategoryListEmptyException {
        if (productRepository.findByModelAndBrandId(productRequest.getModel(), productRequest.getBrandId()).isPresent()) {
            throw new ProductAlreadyExistsException(PRODUCT_WITH_THIS_MODEL_AND_BRAND_ALREADY_EXISTS_IN_CATALOG);
        } else {
            //Generate Product from Request Data
            Product product = new Product();
            product.setName(productRequest.getName());
            product.setDescription(productRequest.getDescription());
            product.setBrand(brandService.getBrand(productRequest.getBrandId()));
            product.setCategory(categoryService.getCategory(productRequest.getCategoryId()));
            product.setSubCategory(subCategoryService.getSubCategory(productRequest.getSubCategoryId()));
            product.setDiscount(productRequest.getDiscount());
            product.setHasVariant(productRequest.isHasVariant());
            product.setModel(productRequest.getModel());
            product.setMrpPrice(productRequest.getMrpPrice());
            product.setSalePrice(productRequest.getSalePrice());
            product.setImages(productRequest.getImages());
            product.setStatus(productRequest.getStatus());
            product.setGstValue(productRequest.getGstValue());
            return productRepository.save(product);
        }
    }
}
