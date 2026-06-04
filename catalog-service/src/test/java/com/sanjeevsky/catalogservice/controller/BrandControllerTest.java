package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.catalogservice.model.Brand;
import com.sanjeevsky.catalogservice.service.BrandService;
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
class BrandControllerTest {

    private static final UUID BRAND_ID = UUID.fromString("db6f19f2-b441-4980-9a4d-4aeeaa7e1534");

    @Mock
    private BrandService brandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BrandController(brandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getBrand_forwardsId() throws Exception {
        when(brandService.getBrand(BRAND_ID)).thenReturn(brand("Puma"));

        mockMvc.perform(get("/catalog-service/getBrand/{id}", BRAND_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(BRAND_ID.toString()));

        verify(brandService).getBrand(BRAND_ID);
    }

    @Test
    void getBrandByName_forwardsName() throws Exception {
        when(brandService.getBrandByName("Puma")).thenReturn(brand("Puma"));

        mockMvc.perform(get("/catalog-service/getBrandByName/{name}", "Puma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Puma"));

        verify(brandService).getBrandByName("Puma");
    }

    @Test
    void getBrands_returnsServiceList() throws Exception {
        when(brandService.getBrandList()).thenReturn(List.of(brand("Puma")));

        mockMvc.perform(get("/catalog-service/getBrands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(brandService).getBrandList();
    }

    @Test
    void addBrand_forwardsNameAndReturns201() throws Exception {
        when(brandService.addBrand("Puma")).thenReturn(brand("Puma"));

        mockMvc.perform(post("/catalog-service/add-brand")
                        .param("name", "Puma"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Brand added"))
                .andExpect(jsonPath("$.data.name").value("Puma"));

        verify(brandService).addBrand("Puma");
    }

    private Brand brand(String name) {
        return Brand.builder()
                .id(BRAND_ID)
                .name(name)
                .build();
    }
}
