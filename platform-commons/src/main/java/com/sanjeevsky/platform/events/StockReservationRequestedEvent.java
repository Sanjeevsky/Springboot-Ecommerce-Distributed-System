package com.sanjeevsky.platform.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationRequestedEvent {
    private UUID orderId;
    private String userId;
    private List<OrderItemEvent> items;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
