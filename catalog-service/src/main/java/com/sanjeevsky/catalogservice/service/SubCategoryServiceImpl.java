package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.SubCategoryListEmptyException;
import com.sanjeevsky.catalogservice.model.SubCategory;
import com.sanjeevsky.catalogservice.repository.SubCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static com.sanjeevsky.catalogservice.service.ProductServiceImpl.SUB_CATEGORY_DOES_NOT_EXISTS;

@Service
public class SubCategoryServiceImpl implements SubCategoryService {
    public static final String CATEGORY_LIST_EMPTY = "Category List Empty ";
    @Autowired
    private SubCategoryRepository subCategoryRepository;

    @Override
    public SubCategory getSubCategory(Long subCategoryId) throws CategoryNotExistsException, SubCategoryListEmptyException {
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
}
