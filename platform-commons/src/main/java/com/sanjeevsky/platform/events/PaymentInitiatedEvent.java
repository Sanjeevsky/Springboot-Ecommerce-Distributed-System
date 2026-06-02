package com.sanjeevsky.platform.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiatedEvent {
    @Builder.Default
    private String eventType = "PAYMENT_INITIATED";
    private UUID paymentId;
    private UUID orderId;
    private String userId;
    private double amount;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
