package com.sanjeevsky.platform.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga compensation command published by order-service (orchestrator) to the
 * {@code payment-commands} topic instructing payment-service to refund a previously
 * successful payment when a later saga step fails.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundPaymentCommand {
    @Builder.Default
    private String eventType = "REFUND_PAYMENT";
    private UUID orderId;
    private UUID paymentId;
    private String userId;
    private double amount;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
