package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.catalogservice.exceptions.InvalidProductRequestException;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductCatalogControllerTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    private static final UUID BRAND_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CATEGORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SUB_CATEGORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProductCatalogController(productService, new ModelMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void addProduct_negativeGstValue_returns400() throws Exception {
        mockMvc.perform(post(addProductUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Widget\",\"model\":\"M1\",\"mrpPrice\":100.0,"
                                + "\"salePrice\":90.0,\"gstValue\":-1.0,\"discount\":0.0,\"status\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("GST value must not be negative")));

        verifyNoInteractions(productService);
    }

    @Test
    void addProduct_serviceInvalidRequest_returns400() throws Exception {
        when(productService.addProduct(eq(BRAND_ID), eq(CATEGORY_ID), eq(SUB_CATEGORY_ID), any(Product.class)))
                .thenThrow(new InvalidProductRequestException("Sale price cannot exceed MRP price"));

        mockMvc.perform(post(addProductUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Widget\",\"model\":\"M1\",\"mrpPrice\":100.0,"
                                + "\"salePrice\":110.0,\"gstValue\":0.0,\"discount\":0.0,\"status\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Sale price cannot exceed MRP price"));

        verify(productService).addProduct(eq(BRAND_ID), eq(CATEGORY_ID), eq(SUB_CATEGORY_ID), any(Product.class));
    }

    private String addProductUrl() {
        return "/catalog-service/product/addProduct?brandId=" + BRAND_ID
                + "&categoryId=" + CATEGORY_ID
                + "&subCategoryId=" + SUB_CATEGORY_ID;
    }
}
