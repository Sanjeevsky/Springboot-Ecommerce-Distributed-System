package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.BrandAlreadyExistsException;
import com.sanjeevsky.catalogservice.exceptions.BrandListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.BrandNotExistsException;
import com.sanjeevsky.catalogservice.model.Brand;
import com.sanjeevsky.catalogservice.service.BrandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class BrandController {
    @Autowired
    private BrandService brandService;

    @GetMapping("/getBrand/{id}")
    ResponseEntity<?> getBrand(@PathVariable UUID id) throws BrandNotExistsException {
        return new ResponseEntity<>(brandService.getBrand(id), HttpStatus.OK);
    }

    @GetMapping("/getBrandByName/{name}")
    ResponseEntity<?> getBrand(@PathVariable String name) throws BrandNotExistsException {
        return new ResponseEntity<>(brandService.getBrandByName(name), HttpStatus.OK);
    }

    @GetMapping("/getBrands")
    ResponseEntity<?> getCategories() throws BrandListEmptyException {
        return new ResponseEntity<>(brandService.getBrandList(), HttpStatus.OK);
    }

    @PostMapping("/add-brand")
    ResponseEntity<?> addBrand(@RequestBody Brand brand) throws BrandAlreadyExistsException {
        return new ResponseEntity<>(brandService.addBrand(brand), HttpStatus.CREATED);
    }
}
