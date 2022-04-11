package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.*;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.dto.ProductDTO;
import com.sanjeevsky.catalogservice.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
    private ResponseEntity<?> getStatus() {
        return new ResponseEntity<String>(SERVICE_UP_AND_HEALTHY, HttpStatus.OK);
    }

    @PostMapping("/addProduct")
    private ResponseEntity<?> addProduct(@RequestParam("categoryId") UUID categoryId, @RequestParam("subCategoryId") UUID subCategoryId, @RequestParam("brandId") UUID brandId, @RequestBody ProductDTO productDTO) throws ProductAlreadyExistsException, CategoryNotExistsException, BrandNotExistsException, SubCategoryNotExistsException, SubCategoryListEmptyException {
        log.info(ADD_PRODUCT_REQUEST_WITH_PRODUCT_ID, productDTO.getName());
        Product product = mapper.map(productDTO, Product.class);
        return new ResponseEntity<Product>(productService.addProduct(brandId, categoryId, subCategoryId, product), HttpStatus.CREATED);
    }

    @GetMapping("/getProduct/{id}")
    ResponseEntity<?> getProduct(@PathVariable("id") UUID uuid) throws NoSuchProductExistsException {
        log.info(GET_PRODUCT_REQUEST_WITH_PRODUCT_ID, uuid);
        return new ResponseEntity<>(productService.getProduct(uuid), HttpStatus.OK);
    }
}
