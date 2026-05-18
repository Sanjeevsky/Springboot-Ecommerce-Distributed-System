package com.sanjeevsky.couponservice.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponValidationResult {

    private boolean valid;
    private String message;
    private double discountAmount;
    private String couponCode;
}
