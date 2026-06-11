package com.sanjeevsky.inventoryservice.controller;

import lombok.Data;

import javax.validation.constraints.Min;

@Data
public class SetStockRequest {

    @Min(value = 0, message = "totalQty must not be negative")
    private int totalQty;
}
