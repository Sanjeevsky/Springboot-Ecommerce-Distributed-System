package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.request.ProductRequest;
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

    @Override
    public Product getProduct(UUID uuid) throws NoSuchProductExistsException {
        Optional<Product> product = productRepository.findById(uuid);
        if (product.isEmpty()) {
            throw new NoSuchProductExistsException(NO_PRODUCT_FOUND_WITH_GIVEN_UUID);
        }
        return product.get();
    }
}
