package com.sanjeevsky.inventoryservice.controller;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Data
public class StockRequest {

    @NotNull(message = "productId is required")
    private UUID productId;

    private UUID variantId;

    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;
}
