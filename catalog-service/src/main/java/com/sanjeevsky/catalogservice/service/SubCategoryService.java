package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.SubCategoryListEmptyException;
import com.sanjeevsky.catalogservice.model.SubCategory;

import java.util.List;

public interface SubCategoryService {

    SubCategory getSubCategory(Long subCategoryId) throws CategoryNotExistsException, SubCategoryListEmptyException;

    List<SubCategory> getAllSubCategory() throws SubCategoryListEmptyException;
}
