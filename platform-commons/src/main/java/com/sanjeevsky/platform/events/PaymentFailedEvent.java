package com.sanjeevsky.platform.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Reply event published by payment-service to the {@code payment-events} topic when a charge
 * fails. The saga orchestrator in order-service consumes it to trigger compensation
 * (release reserved stock + cancel the order).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    @Builder.Default
    private String eventType = "PAYMENT_FAILED";
    private UUID paymentId;
    private UUID orderId;
    private String userId;
    private double amount;
    private String reason;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
