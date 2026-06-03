package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.InvalidCatalogRequestException;
import com.sanjeevsky.catalogservice.exceptions.SubCategoryListEmptyException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.model.SubCategory;
import com.sanjeevsky.catalogservice.repository.CategoryRepository;
import com.sanjeevsky.catalogservice.repository.SubCategoryRepository;
import com.sanjeevsky.catalogservice.service.impl.SubCategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubCategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SubCategoryRepository subCategoryRepository;

    @InjectMocks
    private SubCategoryServiceImpl subCategoryService;

    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID SUB_CATEGORY_ID = UUID.randomUUID();

    private Category category() {
        return Category.builder().id(CATEGORY_ID).categoryName("Electronics").build();
    }

    private SubCategory subCategory(String name) {
        return SubCategory.builder()
                .id(SUB_CATEGORY_ID)
                .category(category())
                .subcategoryName(name)
                .build();
    }

    @Test
    void getSubCategory_exists_returnsSubCategory() {
        SubCategory subCategory = subCategory("Smartphones");
        when(subCategoryRepository.findById(SUB_CATEGORY_ID)).thenReturn(Optional.of(subCategory));

        assertThat(subCategoryService.getSubCategory(SUB_CATEGORY_ID)).isSameAs(subCategory);
    }

    @Test
    void getSubCategory_notFound_throwsSubCategoryListEmptyException() {
        when(subCategoryRepository.findById(SUB_CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subCategoryService.getSubCategory(SUB_CATEGORY_ID))
                .isInstanceOf(SubCategoryListEmptyException.class);
    }

    @Test
    void getSubCategory_nullId_throwsInvalidCatalogRequestException() {
        assertThatThrownBy(() -> subCategoryService.getSubCategory(null))
                .isInstanceOf(InvalidCatalogRequestException.class)
                .hasMessage("Subcategory id is required");

        verifyNoInteractions(categoryRepository, subCategoryRepository);
    }

    @Test
    void getAllSubCategory_empty_throwsSubCategoryListEmptyException() {
        when(subCategoryRepository.findAll()).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> subCategoryService.getAllSubCategory())
                .isInstanceOf(SubCategoryListEmptyException.class);
    }

    @Test
    void addSubCategory_categoryExists_trimsNameAndSaves() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(subCategoryRepository.save(any(SubCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubCategory result = subCategoryService.addSubCategory(CATEGORY_ID, " Smartphones ");

        verify(subCategoryRepository).save(any(SubCategory.class));
        assertThat(result.getSubcategoryName()).isEqualTo("Smartphones");
        assertThat(result.getCategory().getId()).isEqualTo(CATEGORY_ID);
    }

    @Test
    void addSubCategory_blankName_throwsInvalidCatalogRequestException() {
        assertThatThrownBy(() -> subCategoryService.addSubCategory(CATEGORY_ID, " "))
                .isInstanceOf(InvalidCatalogRequestException.class)
                .hasMessageContaining("Subcategory name is required");

        verifyNoInteractions(categoryRepository, subCategoryRepository);
    }

    @Test
    void addSubCategory_missingCategory_throwsCategoryNotExistsException() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subCategoryService.addSubCategory(CATEGORY_ID, "Smartphones"))
                .isInstanceOf(CategoryNotExistsException.class);

        verify(subCategoryRepository, never()).save(any());
    }
}
