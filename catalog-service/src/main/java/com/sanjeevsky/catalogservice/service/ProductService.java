package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface ProductService {
    Product addProduct(UUID categoryId, UUID subCategoryId, UUID brandId, Product product) throws ProductAlreadyExistsException, BrandNotExistsException, CategoryNotExistsException, SubCategoryNotExistsException, SubCategoryListEmptyException;

    Product getProduct(UUID uuid) throws NoSuchProductExistsException;
}
