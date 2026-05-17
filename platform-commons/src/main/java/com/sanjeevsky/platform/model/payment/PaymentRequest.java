package com.sanjeevsky.platform.model.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private UUID orderId;
    private String userId;
    private double amount;
}
