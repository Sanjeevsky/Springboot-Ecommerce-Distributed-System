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
public class OrderCancelledEvent {
    private UUID orderId;
    private String userId;
    private String reason;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
