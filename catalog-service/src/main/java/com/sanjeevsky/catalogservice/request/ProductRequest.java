package com.sanjeevsky.catalogservice.request;

import com.sanjeevsky.catalogservice.model.Variant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductRequest {
    private String name;
    private String description;
    private String model;
    private UUID brandId;
    private UUID categoryId;
    private UUID subCategoryId;
    private double mrpPrice;
    private double salePrice;
    private double gstValue;
    private int status;
    private List<Variant> variants;
    private double discount;
    private ArrayList<String> images;
    private boolean hasVariant;
}
