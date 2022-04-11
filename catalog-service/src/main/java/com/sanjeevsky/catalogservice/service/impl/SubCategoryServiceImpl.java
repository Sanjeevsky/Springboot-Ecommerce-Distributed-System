package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.SubCategoryListEmptyException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.model.SubCategory;
import com.sanjeevsky.catalogservice.repository.CategoryRepository;
import com.sanjeevsky.catalogservice.repository.SubCategoryRepository;
import com.sanjeevsky.catalogservice.service.SubCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.ErrorConstants.*;

@Service
public class SubCategoryServiceImpl implements SubCategoryService {
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private SubCategoryRepository subCategoryRepository;

    @Override
    public SubCategory getSubCategory(UUID subCategoryId) throws CategoryNotExistsException, SubCategoryListEmptyException {
        Optional<SubCategory> category = subCategoryRepository.findById(subCategoryId);
        if (category.isEmpty()) {
            throw new SubCategoryListEmptyException(SUB_CATEGORY_DOES_NOT_EXISTS);
        }
        return category.get();
    }

    @Override
    public List<SubCategory> getAllSubCategory() throws SubCategoryListEmptyException {
        List<SubCategory> subCategories = subCategoryRepository.findAll();
        if (subCategories.isEmpty()) {
            throw new SubCategoryListEmptyException(CATEGORY_LIST_EMPTY);
        }
        return subCategories;
    }

    @Override
    public SubCategory addSubCategory(UUID categoryId, String subcategoryName) throws CategoryNotExistsException {
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isEmpty()) throw new CategoryNotExistsException(CATEGORY_DOES_NOT_EXISTS);
        SubCategory subCategory = SubCategory.builder().category(category.get()).subcategoryName(subcategoryName).build();
        return subCategoryRepository.save(subCategory);
    }

}
