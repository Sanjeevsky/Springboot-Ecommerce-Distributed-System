package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.request.ProductRequest;
import com.sanjeevsky.catalogservice.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/catalog-service")
public class ProductCatalogController {
    @Autowired
    private ProductService repository;

    @GetMapping("/status")
    private ResponseEntity<?> getStatus() {
        return new ResponseEntity("Service Up and Healthy...", HttpStatus.OK);
    }

    @PostMapping("/post-product")
    private ResponseEntity<?> addProduct(@RequestBody ProductRequest productRequest) throws ProductAlreadyExistsException, CategoryNotExistsException, BrandNotExistsException, SubCategoryNotExistsException, SubCategoryListEmptyException {
        return new ResponseEntity<Product>(repository.addProduct(productRequest), HttpStatus.CREATED);
    }
}
