package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.service.CategoryService;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.LoggingConstants.*;

@RestController
@Slf4j
@RequestMapping("/catalog-service")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/getCategory/{id}")
    public ResponseEntity<ApiResponse<Category>> getCategory(@PathVariable UUID id) {
        log.info(GET_CATEGORY_REQUEST_WITH_CATEGORY_ID, id);
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getCategory(id)));
    }

    @GetMapping("/getCategoryByName/{name}")
    public ResponseEntity<ApiResponse<Category>> getCategoryByName(@PathVariable String name) {
        log.info(GET_CATEGORY_REQUEST_WITH_CATEGORY_NAME, name);
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getCategoryName(name)));
    }

    @GetMapping("/getCategories")
    public ResponseEntity<ApiResponse<List<Category>>> getCategories() {
        log.info(GET_ALL_CATEGORY_REQUEST);
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getAllCategory()));
    }

    @PostMapping("/addCategory")
    public ResponseEntity<ApiResponse<Category>> addCategory(@RequestParam("categoryName") String categoryName) {
        log.info(ADD_CATEGORY_REQUEST_WITH_CATEGORY_NAME, categoryName);
        return new ResponseEntity<>(ApiResponse.ok("Category added", categoryService.addCategory(categoryName)), HttpStatus.CREATED);
    }
}
