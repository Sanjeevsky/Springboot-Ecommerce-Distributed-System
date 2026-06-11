package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.catalogservice.model.Brand;
import com.sanjeevsky.catalogservice.service.BrandService;
import com.sanjeevsky.platform.security.AdminAuthorizationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CatalogAdminAuthorizationTest {

    @Mock
    private BrandService brandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BrandController(brandService))
                .addInterceptors(new AdminAuthorizationInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addBrand_withoutRole_returns403() throws Exception {
        mockMvc.perform(post("/catalog-service/add-brand").param("name", "Trove"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Administrator role is required"));

        verifyNoInteractions(brandService);
    }

    @Test
    void addBrand_customerRole_returns403() throws Exception {
        mockMvc.perform(post("/catalog-service/add-brand")
                        .header("X-User-Role", "CUSTOMER")
                        .param("name", "Trove"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(brandService);
    }

    @Test
    void addBrand_adminRole_reachesController() throws Exception {
        when(brandService.addBrand("Trove")).thenReturn(Brand.builder().name("Trove").build());

        mockMvc.perform(post("/catalog-service/add-brand")
                        .header("X-User-Role", "ADMIN")
                        .param("name", "Trove"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Trove"));

        verify(brandService).addBrand("Trove");
    }

    @Test
    void getBrands_withoutRole_remainsPublic() throws Exception {
        when(brandService.getBrandList()).thenReturn(List.of(Brand.builder().name("Trove").build()));

        mockMvc.perform(get("/catalog-service/getBrands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
