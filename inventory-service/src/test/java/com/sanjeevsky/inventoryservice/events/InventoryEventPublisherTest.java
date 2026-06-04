package com.sanjeevsky.inventoryservice.events;

import com.sanjeevsky.platform.events.StockInsufficientEvent;
import com.sanjeevsky.platform.events.StockReservedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryEventPublisherTest {

    private static final UUID ORDER_ID = UUID.fromString("7b7e1ef4-1a49-4d3b-91f8-6537eff101ef");
    private static final UUID PRODUCT_ID = UUID.fromString("cc5652c8-1023-4aa3-9faa-1980a88acf2b");
    private static final String USER_ID = "buyer@example.com";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publishStockReserved_sendsToInventoryEventsWithOrderIdKey() {
        StockReservedEvent event = StockReservedEvent.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .items(List.of())
                .build();

        new InventoryEventPublisher(kafkaTemplate).publishStockReserved(event);

        verify(kafkaTemplate).send(InventoryEventPublisher.INVENTORY_EVENTS_TOPIC, ORDER_ID.toString(), event);
    }

    @Test
    void publishStockInsufficient_sendsToInventoryEventsWithOrderIdKey() {
        StockInsufficientEvent event = StockInsufficientEvent.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .productId(PRODUCT_ID)
                .availableQty(0)
                .requestedQty(2)
                .build();

        new InventoryEventPublisher(kafkaTemplate).publishStockInsufficient(event);

        verify(kafkaTemplate).send(InventoryEventPublisher.INVENTORY_EVENTS_TOPIC, ORDER_ID.toString(), event);
    }
}
