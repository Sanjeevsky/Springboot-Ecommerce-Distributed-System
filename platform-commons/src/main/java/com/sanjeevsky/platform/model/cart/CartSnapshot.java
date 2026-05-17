package com.sanjeevsky.platform.model.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartSnapshot {
    private UUID id;
    private String userId;
    private List<CartItemSnapshot> items;
    private double totalAmount;
}
