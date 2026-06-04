package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.service.CategoryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private static final UUID CATEGORY_ID = UUID.fromString("0cf7d3ab-c3ce-4784-b496-c3c42bb38b28");

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CategoryController(categoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCategory_forwardsId() throws Exception {
        when(categoryService.getCategory(CATEGORY_ID)).thenReturn(category("Footwear"));

        mockMvc.perform(get("/catalog-service/getCategory/{id}", CATEGORY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(CATEGORY_ID.toString()));

        verify(categoryService).getCategory(CATEGORY_ID);
    }

    @Test
    void getCategoryByName_forwardsName() throws Exception {
        when(categoryService.getCategoryName("Footwear")).thenReturn(category("Footwear"));

        mockMvc.perform(get("/catalog-service/getCategoryByName/{name}", "Footwear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoryName").value("Footwear"));

        verify(categoryService).getCategoryName("Footwear");
    }

    @Test
    void getCategories_returnsServiceList() throws Exception {
        when(categoryService.getAllCategory()).thenReturn(List.of(category("Footwear")));

        mockMvc.perform(get("/catalog-service/getCategories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(categoryService).getAllCategory();
    }

    @Test
    void addCategory_forwardsNameAndReturns201() throws Exception {
        when(categoryService.addCategory("Footwear")).thenReturn(category("Footwear"));

        mockMvc.perform(post("/catalog-service/addCategory")
                        .param("categoryName", "Footwear"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category added"))
                .andExpect(jsonPath("$.data.categoryName").value("Footwear"));

        verify(categoryService).addCategory("Footwear");
    }

    private Category category(String name) {
        return Category.builder()
                .id(CATEGORY_ID)
                .categoryName(name)
                .subCategories(List.of())
                .build();
    }
}
