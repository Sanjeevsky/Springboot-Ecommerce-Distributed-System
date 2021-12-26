package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.BrandListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.BrandNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.model.Brand;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.service.BrandService;
import com.sanjeevsky.catalogservice.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class BrandController {
    @Autowired
    private BrandService brandService;

    @GetMapping("/getBrand/{id}")
    ResponseEntity<?> getBrand(@PathVariable long id) throws BrandNotExistsException {
        return new ResponseEntity<>(brandService.getBrand(id), HttpStatus.OK);
    }

    @GetMapping("/getBrands")
    ResponseEntity<?> getCategories() throws BrandListEmptyException {
        return new ResponseEntity<>(brandService.getBrandList(), HttpStatus.OK);
    }

    @PostMapping("/add-brand")
    ResponseEntity<?> addBrand(@RequestBody Brand brand){
        return new ResponseEntity<>(brandService.addBrand(brand),HttpStatus.CREATED);
    }
}
