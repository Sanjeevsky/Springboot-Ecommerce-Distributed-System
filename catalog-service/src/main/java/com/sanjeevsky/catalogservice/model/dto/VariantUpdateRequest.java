package com.sanjeevsky.catalogservice.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VariantUpdateRequest {
    private String condition1Name;
    private String condition1Value;
    private String condition2Name;
    private String condition2Value;
    private Double mrpPrice;
    private Double salePrice;
}
