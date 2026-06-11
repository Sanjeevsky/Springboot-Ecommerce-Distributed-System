package com.sanjeevsky.inventoryservice.controller;

import com.sanjeevsky.inventoryservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.inventoryservice.model.Inventory;
import com.sanjeevsky.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    private static final UUID INVENTORY_ID = UUID.fromString("ca5198ad-1ec5-4a89-9c7e-edec461b750a");
    private static final UUID PRODUCT_ID = UUID.fromString("3b7281d8-92ab-4f0e-890b-458c878f8968");
    private static final UUID VARIANT_ID = UUID.fromString("426ae03a-4b38-4e63-9d30-1eb61696f891");

    @Mock
    private InventoryService inventoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InventoryController(inventoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addStock_validRequest_returnsUpdatedStockAndForwardsValues() throws Exception {
        when(inventoryService.addStock(PRODUCT_ID, VARIANT_ID, 12)).thenReturn(inventory(50, 5));

        mockMvc.perform(post("/inventory-service/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID + "\",\"variantId\":\"" + VARIANT_ID
                                + "\",\"quantity\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Stock updated successfully"))
                .andExpect(jsonPath("$.data.totalQty").value(50));

        verify(inventoryService).addStock(PRODUCT_ID, VARIANT_ID, 12);
    }

    @Test
    void addStock_invalidQuantity_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(post("/inventory-service/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("quantity must be at least 1")));

        verifyNoInteractions(inventoryService);
    }

    @Test
    void getStockByProduct_forwardsProductId() throws Exception {
        when(inventoryService.getStockByProduct(PRODUCT_ID)).thenReturn(List.of(inventory(50, 5)));

        mockMvc.perform(get("/inventory-service/stock/{productId}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(INVENTORY_ID.toString()));

        verify(inventoryService).getStockByProduct(PRODUCT_ID);
    }

    @Test
    void getVariantStock_forwardsProductAndVariantIds() throws Exception {
        when(inventoryService.getStock(PRODUCT_ID, VARIANT_ID)).thenReturn(inventory(50, 5));

        mockMvc.perform(get("/inventory-service/stock/{productId}/variant/{variantId}", PRODUCT_ID, VARIANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.variantId").value(VARIANT_ID.toString()));

        verify(inventoryService).getStock(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    void getAvailableQty_queriesProductStockWithoutVariant() throws Exception {
        when(inventoryService.getStock(PRODUCT_ID, null)).thenReturn(inventory(50, 5));

        mockMvc.perform(get("/inventory-service/stock/{productId}/available", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(45));

        verify(inventoryService).getStock(PRODUCT_ID, null);
    }

    @Test
    void listStock_returnsAllInventory() throws Exception {
        when(inventoryService.listStock()).thenReturn(List.of(inventory(50, 5)));

        mockMvc.perform(get("/inventory-service/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(inventoryService).listStock();
    }

    @Test
    void setVariantStock_forwardsAbsoluteQuantity() throws Exception {
        when(inventoryService.setStock(PRODUCT_ID, VARIANT_ID, 40)).thenReturn(inventory(40, 5));

        mockMvc.perform(put("/inventory-service/stock/{productId}/variant/{variantId}", PRODUCT_ID, VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalQty\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stock quantity set successfully"))
                .andExpect(jsonPath("$.data.totalQty").value(40));

        verify(inventoryService).setStock(PRODUCT_ID, VARIANT_ID, 40);
    }

    @Test
    void setProductStock_forwardsAbsoluteQuantityWithoutVariant() throws Exception {
        Inventory productStock = inventory(40, 5);
        productStock.setVariantId(null);
        when(inventoryService.setStock(PRODUCT_ID, null, 40)).thenReturn(productStock);

        mockMvc.perform(put("/inventory-service/stock/{productId}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalQty\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stock quantity set successfully"))
                .andExpect(jsonPath("$.data.variantId").doesNotExist());

        verify(inventoryService).setStock(PRODUCT_ID, null, 40);
    }

    @Test
    void setProductStock_negativeQuantity_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(put("/inventory-service/stock/{productId}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalQty\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must not be negative")));

        verifyNoInteractions(inventoryService);
    }

    private Inventory inventory(int totalQty, int reservedQty) {
        return Inventory.builder()
                .id(INVENTORY_ID)
                .productId(PRODUCT_ID)
                .variantId(VARIANT_ID)
                .totalQty(totalQty)
                .reservedQty(reservedQty)
                .build();
    }
}
