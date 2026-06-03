package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.catalogservice.exceptions.InvalidVariantRequestException;
import com.sanjeevsky.catalogservice.model.Variant;
import com.sanjeevsky.catalogservice.service.VariantService;
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
class VariantControllerTest {

    @Mock
    private VariantService variantService;

    private MockMvc mockMvc;

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new VariantController(variantService, new ModelMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void addVariant_missingPrimaryCondition_returns400() throws Exception {
        mockMvc.perform(post("/catalog-service/variant/add/" + PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"condition1Value\":\"256GB\",\"mrpPrice\":100.0,\"salePrice\":90.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Primary condition name is required")));

        verifyNoInteractions(variantService);
    }

    @Test
    void addVariant_serviceInvalidRequest_returns400() throws Exception {
        when(variantService.addVariant(eq(PRODUCT_ID), any(Variant.class)))
                .thenThrow(new InvalidVariantRequestException("Sale price cannot exceed MRP price"));

        mockMvc.perform(post("/catalog-service/variant/add/" + PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"condition1Name\":\"Storage\",\"condition1Value\":\"256GB\","
                                + "\"mrpPrice\":100.0,\"salePrice\":110.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Sale price cannot exceed MRP price"));

        verify(variantService).addVariant(eq(PRODUCT_ID), any(Variant.class));
    }
}
