package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.catalogservice.exceptions.InvalidProductRequestException;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.dto.ProductUpdateRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void listProductsForAdmin_forwardsFilters() throws Exception {
        when(productService.listProductsForAdmin("phone", 1, 0, 25, "modifiedAt"))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/catalog-service/product/admin/list")
                        .param("q", "phone")
                        .param("status", "1")
                        .param("page", "0")
                        .param("size", "25")
                        .param("sort", "modifiedAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(productService).listProductsForAdmin("phone", 1, 0, 25, "modifiedAt");
    }

    @Test
    void updateProduct_forwardsPatchAndReturns200() throws Exception {
        Product updated = new Product();
        updated.setId(UUID.randomUUID());
        updated.setName("Updated");
        when(productService.updateProduct(any(UUID.class), any(ProductUpdateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/catalog-service/product/{id}", updated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\",\"salePrice\":80.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product updated successfully"))
                .andExpect(jsonPath("$.data.name").value("Updated"));

        verify(productService).updateProduct(eq(updated.getId()), any(ProductUpdateRequest.class));
    }

    @Test
    void retireProduct_forwardsIdAndReturns200() throws Exception {
        Product retired = new Product();
        retired.setId(UUID.randomUUID());
        retired.setStatus(0);
        when(productService.retireProduct(retired.getId())).thenReturn(retired);

        mockMvc.perform(delete("/catalog-service/product/{id}", retired.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product retired successfully"))
                .andExpect(jsonPath("$.data.status").value(0));

        verify(productService).retireProduct(retired.getId());
    }

    private String addProductUrl() {
        return "/catalog-service/product/addProduct?brandId=" + BRAND_ID
                + "&categoryId=" + CATEGORY_ID
                + "&subCategoryId=" + SUB_CATEGORY_ID;
    }
}
