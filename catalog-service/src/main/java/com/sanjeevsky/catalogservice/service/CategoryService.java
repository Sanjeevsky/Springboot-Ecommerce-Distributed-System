package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.model.Category;

import java.util.List;

public interface CategoryService {
    Category getCategory(Long categoryId) throws CategoryNotExistsException;

    List<Category> getAllCategory() throws CategoryListEmptyException;

    Category addCategory(Category category);
}
