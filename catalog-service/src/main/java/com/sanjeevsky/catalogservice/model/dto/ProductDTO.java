package com.sanjeevsky.catalogservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.util.ArrayList;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductDTO {
    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotBlank(message = "Model is required")
    private String model;

    @Positive(message = "MRP price must be positive")
    private double mrpPrice;

    @Positive(message = "Sale price must be positive")
    private double salePrice;

    @PositiveOrZero(message = "GST value must not be negative")
    private double gstValue;

    @Min(value = 0, message = "Status must be 0 or 1")
    @Max(value = 1, message = "Status must be 0 or 1")
    private int status;

    @PositiveOrZero(message = "Discount must not be negative")
    private double discount;
    private ArrayList<String> images;
    private boolean hasVariant;
}
