package com.sanjeevsky.inventoryservice.controller;

import com.sanjeevsky.inventoryservice.model.Inventory;
import com.sanjeevsky.inventoryservice.service.InventoryService;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory-service")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/stock")
    public ResponseEntity<ApiResponse<Inventory>> addStock(@RequestBody @Valid StockRequest request) {
        log.info("POST /inventory-service/stock - productId={}, variantId={}, quantity={}",
                request.getProductId(), request.getVariantId(), request.getQuantity());
        Inventory inventory = inventoryService.addStock(
                request.getProductId(),
                request.getVariantId(),
                request.getQuantity()
        );
        return ResponseEntity.ok(ApiResponse.ok("Stock updated successfully", inventory));
    }

    @GetMapping("/stock/{productId}")
    public ResponseEntity<ApiResponse<List<Inventory>>> getStockByProduct(@PathVariable UUID productId) {
        log.info("GET /inventory-service/stock/{}", productId);
        List<Inventory> inventories = inventoryService.getStockByProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok(inventories));
    }

    @GetMapping("/stock/{productId}/variant/{variantId}")
    public ResponseEntity<ApiResponse<Inventory>> getVariantStock(
            @PathVariable UUID productId,
            @PathVariable UUID variantId) {
        log.info("GET /inventory-service/stock/{}/variant/{}", productId, variantId);
        Inventory inventory = inventoryService.getStock(productId, variantId);
        return ResponseEntity.ok(ApiResponse.ok(inventory));
    }

    @GetMapping("/stock/{productId}/available")
    public ResponseEntity<ApiResponse<Integer>> getAvailableQty(@PathVariable UUID productId) {
        log.info("GET /inventory-service/stock/{}/available", productId);
        Inventory inventory = inventoryService.getStock(productId, null);
        return ResponseEntity.ok(ApiResponse.ok(inventory.getAvailableQty()));
    }
}
