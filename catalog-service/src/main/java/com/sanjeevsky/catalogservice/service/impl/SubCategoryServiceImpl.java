package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.InvalidCatalogRequestException;
import com.sanjeevsky.catalogservice.exceptions.SubCategoryListEmptyException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.model.SubCategory;
import com.sanjeevsky.catalogservice.repository.CategoryRepository;
import com.sanjeevsky.catalogservice.repository.SubCategoryRepository;
import com.sanjeevsky.catalogservice.service.SubCategoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.ErrorConstants.*;

@Service
public class SubCategoryServiceImpl implements SubCategoryService {
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;

    public SubCategoryServiceImpl(
            CategoryRepository categoryRepository,
            SubCategoryRepository subCategoryRepository) {
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
    }

    @Override
    public SubCategory getSubCategory(UUID subCategoryId){
        if (subCategoryId == null) {
            throw new InvalidCatalogRequestException("Subcategory id is required");
        }
        Optional<SubCategory> category = subCategoryRepository.findById(subCategoryId);
        if (category.isEmpty()) {
            throw new SubCategoryListEmptyException(SUB_CATEGORY_DOES_NOT_EXISTS);
        }
        return category.get();
    }

    @Override
    public List<SubCategory> getAllSubCategory(){
        List<SubCategory> subCategories = subCategoryRepository.findAll();
        if (subCategories.isEmpty()) {
            throw new SubCategoryListEmptyException(CATEGORY_LIST_EMPTY);
        }
        return subCategories;
    }

    @Override
    public SubCategory addSubCategory(UUID categoryId, String subcategoryName){
        if (categoryId == null) {
            throw new InvalidCatalogRequestException("Category id is required");
        }
        String normalizedName = normalizeName(subcategoryName, "Subcategory name is required");
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isEmpty()) throw new CategoryNotExistsException(CATEGORY_DOES_NOT_EXISTS);
        SubCategory subCategory = SubCategory.builder().category(category.get()).subcategoryName(normalizedName).build();
        return subCategoryRepository.save(subCategory);
    }

    private String normalizeName(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidCatalogRequestException(message);
        }
        return value.trim();
    }
}
