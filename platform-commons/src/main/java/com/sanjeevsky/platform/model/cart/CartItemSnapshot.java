package com.sanjeevsky.platform.model.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemSnapshot {
    private UUID id;
    private UUID productId;
    private UUID variantId;
    private String productName;
    private double unitPrice;
    private int qty;
}
