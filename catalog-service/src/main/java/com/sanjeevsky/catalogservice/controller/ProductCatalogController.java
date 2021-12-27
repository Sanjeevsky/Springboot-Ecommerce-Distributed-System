package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.request.ProductRequest;
import com.sanjeevsky.catalogservice.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.LoggingConstants.*;

@Slf4j
@RestController
@RequestMapping("/catalog-service")
public class ProductCatalogController {

    @Autowired
    private ProductService productService;

    @GetMapping("/status")
    private ResponseEntity<?> getStatus() {
        return new ResponseEntity<String>(SERVICE_UP_AND_HEALTHY, HttpStatus.OK);
    }

    @PostMapping("/post-product")
    private ResponseEntity<?> addProduct(@RequestBody ProductRequest productRequest) throws ProductAlreadyExistsException, CategoryNotExistsException, BrandNotExistsException, SubCategoryNotExistsException, SubCategoryListEmptyException {
        log.info(ADD_PRODUCT_REQUEST_WITH_PRODUCT_ID, productRequest.getName());
        return new ResponseEntity<Product>(productService.addProduct(productRequest), HttpStatus.CREATED);
    }

    @GetMapping("/getProduct/{id}")
    ResponseEntity<?> getProduct(@PathVariable("id") UUID uuid) throws NoSuchProductExistsException {
        log.info(GET_PRODUCT_REQUEST_WITH_PRODUCT_ID, uuid);
        return new ResponseEntity<>(productService.getProduct(uuid), HttpStatus.OK);
    }
}
