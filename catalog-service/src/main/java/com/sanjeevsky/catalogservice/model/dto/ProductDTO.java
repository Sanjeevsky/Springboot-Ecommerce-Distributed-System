package com.sanjeevsky.catalogservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
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

    private double gstValue;
    private int status;
    private double discount;
    private ArrayList<String> images;
    private boolean hasVariant;
}
