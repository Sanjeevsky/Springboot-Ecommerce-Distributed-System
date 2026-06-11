package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.catalogservice.service.MediaService;
import com.sanjeevsky.platform.security.AdminAuthorizationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    private static final String BODY = "{\"filename\":\"p.png\",\"contentType\":\"image/png\",\"dataBase64\":\"AQIDBAU=\"}";

    @Mock
    private MediaService mediaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MediaController(mediaService))
                .addInterceptors(new AdminAuthorizationInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void upload_withoutRole_returns403() throws Exception {
        mockMvc.perform(post("/catalog-service/media/upload")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mediaService);
    }

    @Test
    void upload_customerRole_returns403() throws Exception {
        mockMvc.perform(post("/catalog-service/media/upload")
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mediaService);
    }

    @Test
    void upload_adminRole_returnsCreatedWithUrl() throws Exception {
        when(mediaService.uploadProductImage(any()))
                .thenReturn("http://localhost:9000/product-images/products/abc.png");

        mockMvc.perform(post("/catalog-service/media/upload")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value("http://localhost:9000/product-images/products/abc.png"));

        verify(mediaService).uploadProductImage(any());
    }
}
