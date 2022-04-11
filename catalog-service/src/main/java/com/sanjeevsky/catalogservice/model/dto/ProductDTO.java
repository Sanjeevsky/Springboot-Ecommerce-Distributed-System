package com.sanjeevsky.catalogservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductDTO {
    private String name;
    private String description;
    private String model;
    private double mrpPrice;
    private double salePrice;
    private double gstValue;
    private int status;
    private double discount;
    private ArrayList<String> images;
    private boolean hasVariant;
}
