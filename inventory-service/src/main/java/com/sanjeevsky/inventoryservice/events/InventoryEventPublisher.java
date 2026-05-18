package com.sanjeevsky.inventoryservice.events;

import com.sanjeevsky.platform.events.StockInsufficientEvent;
import com.sanjeevsky.platform.events.StockReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

    static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishStockReserved(StockReservedEvent event) {
        log.info("Publishing StockReservedEvent for orderId={}", event.getOrderId());
        kafkaTemplate.send(INVENTORY_EVENTS_TOPIC, event.getOrderId().toString(), event);
    }

    public void publishStockInsufficient(StockInsufficientEvent event) {
        log.info("Publishing StockInsufficientEvent for orderId={}, productId={}", event.getOrderId(), event.getProductId());
        kafkaTemplate.send(INVENTORY_EVENTS_TOPIC, event.getOrderId().toString(), event);
    }
}
