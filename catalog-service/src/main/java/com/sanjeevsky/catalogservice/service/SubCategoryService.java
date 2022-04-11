package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.SubCategoryListEmptyException;
import com.sanjeevsky.catalogservice.model.SubCategory;

import java.util.List;
import java.util.UUID;

public interface SubCategoryService {

    SubCategory getSubCategory(UUID subCategoryId) throws CategoryNotExistsException, SubCategoryListEmptyException;

    List<SubCategory> getAllSubCategory() throws SubCategoryListEmptyException;

    SubCategory addSubCategory(UUID categoryId, String subcategoryName) throws CategoryNotExistsException;
}
