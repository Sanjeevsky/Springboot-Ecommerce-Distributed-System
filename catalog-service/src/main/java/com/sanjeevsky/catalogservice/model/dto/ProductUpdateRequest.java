package com.sanjeevsky.catalogservice.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.UUID;

@Getter
@Setter
public class ProductUpdateRequest {
    private String name;
    private String description;
    private String model;
    private Double mrpPrice;
    private Double salePrice;
    private Double gstValue;
    private Double discount;
    private Integer status;
    private Boolean hasVariant;
    private ArrayList<String> images;
    private UUID brandId;
    private UUID categoryId;
    private UUID subCategoryId;
}
