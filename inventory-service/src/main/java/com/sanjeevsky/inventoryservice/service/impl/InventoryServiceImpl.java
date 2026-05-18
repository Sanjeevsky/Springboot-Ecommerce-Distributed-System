package com.sanjeevsky.inventoryservice.service.impl;

import com.sanjeevsky.inventoryservice.exceptions.InsufficientStockException;
import com.sanjeevsky.inventoryservice.exceptions.InventoryNotFoundException;
import com.sanjeevsky.inventoryservice.model.Inventory;
import com.sanjeevsky.inventoryservice.model.InventoryTransaction;
import com.sanjeevsky.inventoryservice.repository.InventoryRepository;
import com.sanjeevsky.inventoryservice.repository.InventoryTransactionRepository;
import com.sanjeevsky.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;

    @Override
    @Transactional
    public Inventory addStock(UUID productId, UUID variantId, int quantity) {
        log.info("Adding stock: productId={}, variantId={}, quantity={}", productId, variantId, quantity);

        Optional<Inventory> existing = variantId != null
                ? inventoryRepository.findByProductIdAndVariantId(productId, variantId)
                : inventoryRepository.findByProductId(productId);

        Inventory inventory = existing.orElseGet(() -> Inventory.builder()
                .productId(productId)
                .variantId(variantId)
                .build());

        inventory.setTotalQty(inventory.getTotalQty() + quantity);
        inventory = inventoryRepository.save(inventory);

        InventoryTransaction tx = InventoryTransaction.builder()
                .inventoryId(inventory.getId())
                .type("RESTOCK")
                .quantity(quantity)
                .build();
        transactionRepository.save(tx);

        log.info("Stock added for productId={}, variantId={}, newTotal={}", productId, variantId, inventory.getTotalQty());
        return inventory;
    }

    @Override
    public Inventory getStock(UUID productId, UUID variantId) {
        if (variantId != null) {
            return inventoryRepository.findByProductIdAndVariantId(productId, variantId)
                    .orElseThrow(() -> new InventoryNotFoundException(
                            "Inventory not found for productId=" + productId + " variantId=" + variantId));
        }
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found for productId=" + productId));
    }

    @Override
    public Inventory getStockById(UUID inventoryId) {
        return inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found for id=" + inventoryId));
    }

    @Override
    public List<Inventory> getStockByProduct(UUID productId) {
        return inventoryRepository.findAllByProductId(productId);
    }

    @Override
    @Transactional
    public Inventory reserveStock(UUID orderId, UUID productId, UUID variantId, int qty) {
        log.info("Reserving stock: orderId={}, productId={}, variantId={}, qty={}", orderId, productId, variantId, qty);

        Inventory inventory = getStock(productId, variantId);

        int available = inventory.getAvailableQty();
        if (available < qty) {
            throw new InsufficientStockException(
                    "Insufficient stock for productId=" + productId + " variantId=" + variantId
                            + ". Available=" + available + ", requested=" + qty);
        }

        inventory.setReservedQty(inventory.getReservedQty() + qty);
        inventory = inventoryRepository.save(inventory);

        InventoryTransaction tx = InventoryTransaction.builder()
                .inventoryId(inventory.getId())
                .orderId(orderId)
                .type("RESERVE")
                .quantity(qty)
                .build();
        transactionRepository.save(tx);

        log.info("Stock reserved for productId={}, variantId={}, reservedQty={}", productId, variantId, inventory.getReservedQty());
        return inventory;
    }

    @Override
    @Transactional
    public Inventory releaseStock(UUID orderId, UUID productId, UUID variantId, int qty) {
        log.info("Releasing stock: orderId={}, productId={}, variantId={}, qty={}", orderId, productId, variantId, qty);

        Inventory inventory = getStock(productId, variantId);

        int newReserved = Math.max(0, inventory.getReservedQty() - qty);
        inventory.setReservedQty(newReserved);
        inventory = inventoryRepository.save(inventory);

        InventoryTransaction tx = InventoryTransaction.builder()
                .inventoryId(inventory.getId())
                .orderId(orderId)
                .type("RELEASE")
                .quantity(qty)
                .build();
        transactionRepository.save(tx);

        log.info("Stock released for productId={}, variantId={}, reservedQty={}", productId, variantId, inventory.getReservedQty());
        return inventory;
    }
}
