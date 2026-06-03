package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.CategoryAlreadyExistsException;
import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.InvalidCatalogRequestException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.repository.CategoryRepository;
import com.sanjeevsky.catalogservice.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.ErrorConstants.CATEGORY_DOES_NOT_EXISTS;
import static com.sanjeevsky.catalogservice.utils.ErrorConstants.NOT_CATEGORY_FOUND_EXCEPTION;

@Service
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Category getCategory(UUID categoryId){
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isEmpty()) {
            throw new CategoryNotExistsException(CATEGORY_DOES_NOT_EXISTS);
        }
        return category.get();
    }

    @Override
    public Category getCategoryName(String name){
        String categoryName = normalizeName(name, "Category name is required");
        Optional<Category> category = categoryRepository.findOneByCategoryName(categoryName);
        if (category.isEmpty()) {
            throw new CategoryNotExistsException(CATEGORY_DOES_NOT_EXISTS);
        }
        return category.get();
    }

    @Override
    public List<Category> getAllCategory(){
        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) {
            throw new CategoryListEmptyException(NOT_CATEGORY_FOUND_EXCEPTION);
        }
        return categories;
    }

    @Override
    public Category addCategory(String categoryName){
        String normalizedName = normalizeName(categoryName, "Category name is required");
        Optional<Category> category = categoryRepository.findOneByCategoryName(normalizedName);
        if (category.isPresent()) throw new CategoryAlreadyExistsException("Category with given name already exists.");
        Category build = Category.builder().categoryName(normalizedName).build();
        return categoryRepository.save(build);
    }

    private String normalizeName(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidCatalogRequestException(message);
        }
        return value.trim();
    }
}
