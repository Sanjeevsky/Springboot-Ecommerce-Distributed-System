package com.sanjeevsky.platform.model.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private UUID id;
    private String name;
    private String description;
    private double salePrice;
    private double mrpPrice;
    private double discount;
    private int status;
    private boolean hasVariant;
}
