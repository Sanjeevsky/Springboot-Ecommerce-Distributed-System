package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.model.SubCategory;
import com.sanjeevsky.catalogservice.service.SubCategoryService;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.LoggingConstants.ADD_SUB_CATEGORY_REQUEST_WITH_CATEGORY_ID_AND_SUB_CATEGORY_NAME;

@RestController
@RequestMapping("/catalog-service")
@Slf4j
public class SubCategoryController {

    private final SubCategoryService subCategoryService;

    public SubCategoryController(SubCategoryService subCategoryService) {
        this.subCategoryService = subCategoryService;
    }

    @PostMapping("/add-subcategory/{category-id}")
    public ResponseEntity<ApiResponse<SubCategory>> addSubCategory(
            @PathVariable(name = "category-id") UUID categoryId,
            @RequestParam("subcategoryName") String subcategoryName) {
        log.info(ADD_SUB_CATEGORY_REQUEST_WITH_CATEGORY_ID_AND_SUB_CATEGORY_NAME, categoryId, subcategoryName);
        return new ResponseEntity<>(
                ApiResponse.ok("SubCategory added", subCategoryService.addSubCategory(categoryId, subcategoryName)),
                HttpStatus.CREATED);
    }
}
