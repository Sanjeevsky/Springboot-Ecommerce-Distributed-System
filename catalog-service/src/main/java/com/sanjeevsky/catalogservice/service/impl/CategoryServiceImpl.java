package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.CategoryAlreadyExistsException;
import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.repository.CategoryRepository;
import com.sanjeevsky.catalogservice.service.CategoryService;
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
    public Category addCategory(String categoryName) throws CategoryAlreadyExistsException {
        Optional<Category> category = categoryRepository.findOneByCategoryName(categoryName);
        if (category.isPresent()) throw new CategoryAlreadyExistsException("Category with given name already exists.");
        Category build = Category.builder().categoryName(categoryName).build();
        return categoryRepository.save(build);
    }
}
