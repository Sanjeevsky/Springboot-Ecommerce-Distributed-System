package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.model.SubCategory;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    Category getCategory(UUID categoryId) throws CategoryNotExistsException;

    Category getCategoryName(String name) throws CategoryNotExistsException;

    List<Category> getAllCategory() throws CategoryListEmptyException;

    Category addCategory(Category category);

    Category addSubCategory(UUID categoryId, SubCategory category) throws CategoryNotExistsException;
}
