package com.sanjeevsky.catalogservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VariantDTO {
    private UUID id;
    private String condition1Name;
    private String condition1Value;
    private String condition2Name;
    private String condition2Value;
    private double mrpPrice;
    private double salePrice;
}
