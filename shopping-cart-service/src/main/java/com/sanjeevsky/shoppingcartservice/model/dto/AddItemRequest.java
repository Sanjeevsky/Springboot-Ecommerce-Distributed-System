package com.sanjeevsky.shoppingcartservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddItemRequest {

    private UUID productId;
    private UUID variantId;
    private int qty;
}
