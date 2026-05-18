package com.sanjeevsky.platform.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemEvent {
    private UUID productId;
    private UUID variantId;
    private String productName;
    private double unitPrice;
    private int qty;
}
