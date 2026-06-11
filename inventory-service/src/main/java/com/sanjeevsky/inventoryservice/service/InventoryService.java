package com.sanjeevsky.inventoryservice.service;

import com.sanjeevsky.inventoryservice.model.Inventory;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    Inventory addStock(UUID productId, UUID variantId, int quantity);

    Inventory setStock(UUID productId, UUID variantId, int totalQty);

    List<Inventory> listStock();

    Inventory getStock(UUID productId, UUID variantId);

    Inventory getStockById(UUID inventoryId);

    List<Inventory> getStockByProduct(UUID productId);

    Inventory reserveStock(UUID orderId, UUID productId, UUID variantId, int qty);

    Inventory releaseStock(UUID orderId, UUID productId, UUID variantId, int qty);
}
