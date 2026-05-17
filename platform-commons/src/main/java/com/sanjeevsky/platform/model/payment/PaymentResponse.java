package com.sanjeevsky.platform.model.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private UUID orderId;
    private String userId;
    private double amount;
    private String currency;
    private PaymentStatus status;
}
