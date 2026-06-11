package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.dto.ProductUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public interface ProductService {
    Product addProduct(UUID categoryId, UUID subCategoryId, UUID brandId, Product product);

    Product getProduct(UUID uuid);

    Page<Product> listProducts(int page, int size, String sort);

    Page<Product> listProductsForAdmin(String keyword, Integer status, int page, int size, String sort);

    Page<Product> searchProducts(String keyword, UUID categoryId, UUID brandId, int page, int size);

    List<String> suggestProducts(String prefix, int size);

    Product updateProduct(UUID productId, ProductUpdateRequest request);

    Product retireProduct(UUID productId);
}
