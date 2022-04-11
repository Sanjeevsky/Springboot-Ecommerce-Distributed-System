package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.service.SubCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.LoggingConstants.ADD_SUB_CATEGORY_REQUEST_WITH_CATEGORY_ID_AND_SUB_CATEGORY_NAME;

@RestController
@RequestMapping("/catalog-service/")
@Slf4j
public class SubCategoryController {
    @Autowired
    private SubCategoryService subCategoryService;

    @PostMapping("/add-subcategory/{category-id}")
    ResponseEntity<?> addSubCategory(@PathVariable(name = "category-id") UUID categoryId, @RequestParam("subcategoryName") String subcategoryName) throws CategoryNotExistsException {
        log.info(ADD_SUB_CATEGORY_REQUEST_WITH_CATEGORY_ID_AND_SUB_CATEGORY_NAME, categoryId, subcategoryName);
        return new ResponseEntity<>(subCategoryService.addSubCategory(categoryId, subcategoryName), HttpStatus.CREATED);
    }
}
