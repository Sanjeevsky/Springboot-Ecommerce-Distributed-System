package com.sanjeevsky.catalogservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VariantDTO {
    private UUID id;
    @NotBlank(message = "Primary condition name is required")
    private String condition1Name;
    @NotBlank(message = "Primary condition value is required")
    private String condition1Value;
    private String condition2Name;
    private String condition2Value;
    @Positive(message = "MRP price must be positive")
    private double mrpPrice;
    @Positive(message = "Sale price must be positive")
    private double salePrice;
}
