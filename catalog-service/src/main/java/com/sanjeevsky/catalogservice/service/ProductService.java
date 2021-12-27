package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.request.ProductRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface ProductService {
    Product addProduct(ProductRequest productRequest) throws ProductAlreadyExistsException, BrandNotExistsException, CategoryNotExistsException, SubCategoryNotExistsException, SubCategoryListEmptyException;

    Product getProduct(UUID uuid) throws NoSuchProductExistsException;
}
