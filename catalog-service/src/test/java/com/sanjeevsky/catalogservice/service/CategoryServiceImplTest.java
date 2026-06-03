package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.CategoryAlreadyExistsException;
import com.sanjeevsky.catalogservice.exceptions.CategoryListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.CategoryNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.InvalidCatalogRequestException;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.repository.CategoryRepository;
import com.sanjeevsky.catalogservice.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private Category category(String name) {
        return Category.builder().id(CATEGORY_ID).categoryName(name).build();
    }

    // ─── getCategory ──────────────────────────────────────────────────────────

    @Test
    void getCategory_exists_returnsCategory() {
        Category c = category("Electronics");
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(c));

        assertThat(categoryService.getCategory(CATEGORY_ID)).isSameAs(c);
    }

    @Test
    void getCategory_notFound_throwsCategoryNotExistsException() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategory(CATEGORY_ID))
                .isInstanceOf(CategoryNotExistsException.class);
    }

    // ─── getCategoryName ──────────────────────────────────────────────────────

    @Test
    void getCategoryName_exists_returnsCategory() {
        Category c = category("Clothing");
        when(categoryRepository.findOneByCategoryName("Clothing")).thenReturn(Optional.of(c));

        assertThat(categoryService.getCategoryName("Clothing")).isSameAs(c);
    }

    @Test
    void getCategoryName_notFound_throwsCategoryNotExistsException() {
        when(categoryRepository.findOneByCategoryName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryName("Missing"))
                .isInstanceOf(CategoryNotExistsException.class);
    }

    // ─── getAllCategory ───────────────────────────────────────────────────────

    @Test
    void getAllCategory_nonEmpty_returnsList() {
        List<Category> cats = List.of(category("Electronics"), category("Clothing"));
        when(categoryRepository.findAll()).thenReturn(cats);

        assertThat(categoryService.getAllCategory()).hasSize(2);
    }

    @Test
    void getAllCategory_empty_throwsCategoryListEmptyException() {
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> categoryService.getAllCategory())
                .isInstanceOf(CategoryListEmptyException.class);
    }

    // ─── addCategory ──────────────────────────────────────────────────────────

    @Test
    void addCategory_newName_savesAndReturnsCategory() {
        when(categoryRepository.findOneByCategoryName("Sports")).thenReturn(Optional.empty());
        Category saved = category("Sports");
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        Category result = categoryService.addCategory("Sports");

        verify(categoryRepository).save(any(Category.class));
        assertThat(result.getCategoryName()).isEqualTo("Sports");
    }

    @Test
    void addCategory_duplicateName_throwsCategoryAlreadyExistsException() {
        when(categoryRepository.findOneByCategoryName("Electronics"))
                .thenReturn(Optional.of(category("Electronics")));

        assertThatThrownBy(() -> categoryService.addCategory("Electronics"))
                .isInstanceOf(CategoryAlreadyExistsException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void addCategory_blankName_throwsInvalidCatalogRequestException() {
        assertThatThrownBy(() -> categoryService.addCategory(" "))
                .isInstanceOf(InvalidCatalogRequestException.class)
                .hasMessageContaining("Category name is required");

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void addCategory_trimsNameBeforeLookupAndSave() {
        when(categoryRepository.findOneByCategoryName("Sports")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category result = categoryService.addCategory(" Sports ");

        verify(categoryRepository).findOneByCategoryName("Sports");
        assertThat(result.getCategoryName()).isEqualTo("Sports");
    }
}
