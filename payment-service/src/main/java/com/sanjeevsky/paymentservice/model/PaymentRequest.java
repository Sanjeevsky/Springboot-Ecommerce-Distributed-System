package com.sanjeevsky.paymentservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    @NotNull(message = "orderId is required")
    private UUID orderId;

    @NotBlank(message = "userId is required")
    private String userId;

    @Positive(message = "amount must be greater than zero")
    private double amount;

    private String idempotencyKey;

    public PaymentRequest(UUID orderId, String userId, double amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
    }
}
