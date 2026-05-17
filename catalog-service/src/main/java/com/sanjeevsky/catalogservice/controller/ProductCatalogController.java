package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.dto.ProductDTO;
import com.sanjeevsky.catalogservice.service.ProductService;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.LoggingConstants.*;

@Slf4j
@RestController
@RequestMapping("/catalog-service/product/")
public class ProductCatalogController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ModelMapper mapper;

    @GetMapping("/status")
    private ResponseEntity<ApiResponse<?>> getStatus() {
        return new ResponseEntity<>(ApiResponse.ok("Catalog service is up and healthy", null), HttpStatus.OK);
    }

    @PostMapping("/addProduct")
    private ResponseEntity<ApiResponse<Product>> addProduct(@RequestParam("categoryId") UUID categoryId, @RequestParam("subCategoryId") UUID subCategoryId, @RequestParam("brandId") UUID brandId, @RequestBody ProductDTO productDTO) throws ProductAlreadyExistsException, CategoryNotExistsException, BrandNotExistsException, SubCategoryNotExistsException, SubCategoryListEmptyException {
        log.info(ADD_PRODUCT_REQUEST_WITH_PRODUCT_ID, productDTO.getName());
        Product product = mapper.map(productDTO, Product.class);
        Product saved = productService.addProduct(brandId, categoryId, subCategoryId, product);
        return new ResponseEntity<>(ApiResponse.ok("Product added successfully", saved), HttpStatus.CREATED);
    }

    @GetMapping("/getProduct/{id}")
    ResponseEntity<ApiResponse<Product>> getProduct(@PathVariable("id") UUID uuid) throws NoSuchProductExistsException {
        log.info(GET_PRODUCT_REQUEST_WITH_PRODUCT_ID, uuid);
        Product product = productService.getProduct(uuid);
        return new ResponseEntity<>(ApiResponse.ok(product), HttpStatus.OK);
    }

    @GetMapping("/list")
    ResponseEntity<ApiResponse<Page<Product>>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort) {
        log.info(LIST_PRODUCTS_REQUEST, page, size, sort);
        Page<Product> products = productService.listProducts(page, size, sort);
        return new ResponseEntity<>(ApiResponse.ok(products), HttpStatus.OK);
    }

    @GetMapping("/search")
    ResponseEntity<ApiResponse<Page<Product>>> searchProducts(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info(SEARCH_PRODUCTS_REQUEST, q, categoryId, brandId);
        Page<Product> products = productService.searchProducts(q, categoryId, brandId, page, size);
        return new ResponseEntity<>(ApiResponse.ok(products), HttpStatus.OK);
    }
}
