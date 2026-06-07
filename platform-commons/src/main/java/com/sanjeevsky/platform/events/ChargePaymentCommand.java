package com.sanjeevsky.platform.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga command published by order-service (orchestrator) to the {@code payment-commands} topic
 * instructing payment-service to charge for an order. Part of the orchestration-based saga;
 * {@code simulateFailure} is a learning lever to deterministically exercise the compensation path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargePaymentCommand {
    @Builder.Default
    private String eventType = "CHARGE_PAYMENT";
    private UUID orderId;
    private String userId;
    private double amount;
    private String idempotencyKey;
    private boolean simulateFailure;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
