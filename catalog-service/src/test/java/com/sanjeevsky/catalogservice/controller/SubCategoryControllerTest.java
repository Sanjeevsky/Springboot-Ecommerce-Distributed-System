package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.model.SubCategory;
import com.sanjeevsky.catalogservice.service.SubCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubCategoryControllerTest {

    private static final UUID CATEGORY_ID = UUID.fromString("3382d0cb-dd6e-43e2-8122-7f89d2b790de");
    private static final UUID SUB_CATEGORY_ID = UUID.fromString("450f09cf-c207-4d26-86d9-6967f28d3ebb");

    @Mock
    private SubCategoryService subCategoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SubCategoryController(subCategoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addSubCategory_forwardsCategoryIdAndName() throws Exception {
        when(subCategoryService.addSubCategory(CATEGORY_ID, "Sneakers")).thenReturn(subCategory());

        mockMvc.perform(post("/catalog-service/add-subcategory/{category-id}", CATEGORY_ID)
                        .param("subcategoryName", "Sneakers"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("SubCategory added"))
                .andExpect(jsonPath("$.data.id").value(SUB_CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.data.subcategoryName").value("Sneakers"));

        verify(subCategoryService).addSubCategory(CATEGORY_ID, "Sneakers");
    }

    private SubCategory subCategory() {
        Category category = Category.builder()
                .id(CATEGORY_ID)
                .categoryName("Footwear")
                .subCategories(List.of())
                .build();
        return SubCategory.builder()
                .id(SUB_CATEGORY_ID)
                .subcategoryName("Sneakers")
                .category(category)
                .build();
    }
}
