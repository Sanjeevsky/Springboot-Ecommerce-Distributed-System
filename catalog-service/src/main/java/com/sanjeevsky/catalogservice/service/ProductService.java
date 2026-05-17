package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface ProductService {
    Product addProduct(UUID categoryId, UUID subCategoryId, UUID brandId, Product product) throws ProductAlreadyExistsException, BrandNotExistsException, CategoryNotExistsException, SubCategoryNotExistsException, SubCategoryListEmptyException;

    Product getProduct(UUID uuid) throws NoSuchProductExistsException;

    Page<Product> listProducts(int page, int size, String sort);

    Page<Product> searchProducts(String keyword, UUID categoryId, UUID brandId, int page, int size);
}
