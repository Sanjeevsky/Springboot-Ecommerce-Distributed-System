package com.sanjeevsky.catalogservice.request;

import com.sanjeevsky.catalogservice.model.Discount;
import com.sanjeevsky.catalogservice.model.Variant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductRequest {
    private String name;
    private String description;
    private String model;
    private long brandId;
    private long categoryId;
    private long subCategoryId;
    private double mrpPrice;
    private double salePrice;
    private int status;
    private List<Variant> variants;
    private List<Discount> discounts;
    private ArrayList<String> images;
    private boolean hasVariant;
}
