package com.sanjeevsky.orderservice.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InventoryStock {
    private UUID id;
    private UUID productId;
    private UUID variantId;
    private int totalQty;
    private int reservedQty;
    private int availableQty;

    public int availableQuantity() {
        return Math.max(0, totalQty - reservedQty);
    }
}
