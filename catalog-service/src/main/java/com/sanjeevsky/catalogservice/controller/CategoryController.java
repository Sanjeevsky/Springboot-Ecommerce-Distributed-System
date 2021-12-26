package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    @GetMapping("/getCategory/{id}")
    ResponseEntity<?> getCategory(@PathVariable long id) throws CategoryNotExistsException {
        return new ResponseEntity<>(categoryService.getCategory(id), HttpStatus.OK);
    }

    @GetMapping("/getCategories")
    ResponseEntity<?> getCategories() throws  CategoryListEmptyException {
        return new ResponseEntity<>(categoryService.getAllCategory(), HttpStatus.OK);
    }

    @PostMapping("/add-category")
    ResponseEntity<?> addCategory(@RequestBody Category category){
        return new ResponseEntity<>(categoryService.addCategory(category),HttpStatus.CREATED);
    }
}
