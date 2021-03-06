package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.CategoryAlreadyExistsException;
import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.LoggingConstants.*;

@RestController
@Slf4j
@RequestMapping("/catalog-service/")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    @GetMapping("/getCategory/{id}")
    ResponseEntity<?> getCategory(@PathVariable UUID id) throws CategoryNotExistsException {
        log.info(GET_CATEGORY_REQUEST_WITH_CATEGORY_ID, id);
        return new ResponseEntity<>(categoryService.getCategory(id), HttpStatus.OK);
    }

    @GetMapping("/getCategoryByName/{name}")
    ResponseEntity<?> getCategoryName(@PathVariable String name) throws CategoryNotExistsException {
        log.info(GET_CATEGORY_REQUEST_WITH_CATEGORY_NAME, name);
        return new ResponseEntity<>(categoryService.getCategoryName(name), HttpStatus.OK);
    }

    @GetMapping("/getCategories")
    ResponseEntity<?> getCategories() throws CategoryListEmptyException {
        log.info(GET_ALL_CATEGORY_REQUEST);
        return new ResponseEntity<>(categoryService.getAllCategory(), HttpStatus.OK);
    }

    @PostMapping("/addCategory")
    ResponseEntity<?> addCategory(@RequestParam("categoryName") String categoryName) throws CategoryAlreadyExistsException {
        log.info(ADD_CATEGORY_REQUEST_WITH_CATEGORY_NAME, categoryName);
        return new ResponseEntity<>(categoryService.addCategory(categoryName), HttpStatus.CREATED);
    }
}
