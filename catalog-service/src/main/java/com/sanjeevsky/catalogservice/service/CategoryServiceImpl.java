package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.model.SubCategory;
import com.sanjeevsky.catalogservice.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.ErrorConstants.CATEGORY_DOES_NOT_EXISTS;
import static com.sanjeevsky.catalogservice.utils.ErrorConstants.NOT_CATEGORY_FOUND_EXCEPTION;

@Service
public class CategoryServiceImpl implements CategoryService {
    @Autowired
    private CategoryRepository categoryRepository;

    @Override
    public Category getCategory(UUID categoryId) throws CategoryNotExistsException {
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isEmpty()) {
            throw new CategoryNotExistsException(CATEGORY_DOES_NOT_EXISTS);
        }
        return category.get();
    }

    @Override
    public Category getCategoryName(String name) throws CategoryNotExistsException {
        Optional<Category> category = categoryRepository.findOneByCategoryName(name);
        if (category.isEmpty()) {
            throw new CategoryNotExistsException(CATEGORY_DOES_NOT_EXISTS);
        }
        return category.get();
    }

    @Override
    public List<Category> getAllCategory() throws CategoryListEmptyException {
        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) {
            throw new CategoryListEmptyException(NOT_CATEGORY_FOUND_EXCEPTION);
        }
        return categories;
    }

    @Override
    public Category addCategory(Category category) {
        return categoryRepository.save(category);
    }

    @Override
    public Category addSubCategory(UUID categoryId, SubCategory subcategory) throws CategoryNotExistsException {
        Optional<Category> optional = categoryRepository.findById(categoryId);
        if (optional.isEmpty()) {
            throw new CategoryNotExistsException(CATEGORY_DOES_NOT_EXISTS);
        }
        Category category = optional.get();
        category.getSubCategories().add(subcategory);
        return categoryRepository.save(category);
    }
}
