package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.BrandAlreadyExistsException;
import com.sanjeevsky.catalogservice.exceptions.BrandListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.BrandNotExistsException;
import com.sanjeevsky.catalogservice.service.BrandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.LoggingConstants.*;

@RestController
@Slf4j
@RequestMapping("/catalog-service")
public class BrandController {
    @Autowired
    private BrandService brandService;

    @GetMapping("/getBrand/{id}")
    ResponseEntity<?> getBrand(@PathVariable UUID id) throws BrandNotExistsException {
        log.info(GET_BRAND_REQUEST_WITH_BRAND_ID, id);
        return new ResponseEntity<>(brandService.getBrand(id), HttpStatus.OK);
    }

    @GetMapping("/getBrandByName/{name}")
    ResponseEntity<?> getBrand(@PathVariable String name) throws BrandNotExistsException {
        log.info(GET_BRAND_REQUEST_WITH_BRAND_NAME, name);
        return new ResponseEntity<>(brandService.getBrandByName(name), HttpStatus.OK);
    }

    @GetMapping("/getBrands")
    ResponseEntity<?> getCategories() throws BrandListEmptyException {
        log.info(GET_ALL_BRAND_REQUEST);
        return new ResponseEntity<>(brandService.getBrandList(), HttpStatus.OK);
    }

    @PostMapping("/add-brand")
    ResponseEntity<?> addBrand(@RequestParam("name") String name) throws BrandAlreadyExistsException {
        log.info(ADD_BRAND_REQUEST_WITH_BRAND_NAME, name);
        return new ResponseEntity<>(brandService.addBrand(name), HttpStatus.CREATED);
    }

}
