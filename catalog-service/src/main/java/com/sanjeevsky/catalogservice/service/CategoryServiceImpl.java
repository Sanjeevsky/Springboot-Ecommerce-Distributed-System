package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.service.ProductServiceImpl.CATEGORY_DOES_NOT_EXISTS;

@Service
public class CategoryServiceImpl implements CategoryService {
    public static final String NOT_CATEGORY_FOUND_EXCEPTION = "Not Category Found Exception.";
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
}
