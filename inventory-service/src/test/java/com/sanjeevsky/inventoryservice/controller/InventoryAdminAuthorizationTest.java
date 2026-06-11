package com.sanjeevsky.inventoryservice.controller;

import com.sanjeevsky.inventoryservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.inventoryservice.model.Inventory;
import com.sanjeevsky.inventoryservice.service.InventoryService;
import com.sanjeevsky.platform.security.AdminAuthorizationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InventoryAdminAuthorizationTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private InventoryService inventoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InventoryController(inventoryService))
                .addInterceptors(new AdminAuthorizationInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void setStock_customerRole_returns403() throws Exception {
        mockMvc.perform(put("/inventory-service/stock/{productId}", PRODUCT_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalQty\":20}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(inventoryService);
    }

    @Test
    void setStock_adminRole_reachesService() throws Exception {
        when(inventoryService.setStock(PRODUCT_ID, null, 20))
                .thenReturn(Inventory.builder().productId(PRODUCT_ID).totalQty(20).build());

        mockMvc.perform(put("/inventory-service/stock/{productId}", PRODUCT_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalQty\":20}"))
                .andExpect(status().isOk());

        verify(inventoryService).setStock(PRODUCT_ID, null, 20);
    }
}
