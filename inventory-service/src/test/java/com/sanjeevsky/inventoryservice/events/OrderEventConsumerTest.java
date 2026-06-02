package com.sanjeevsky.inventoryservice.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.inventoryservice.repository.InventoryTransactionRepository;
import com.sanjeevsky.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private InventoryEventPublisher eventPublisher;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer(inventoryService, eventPublisher, transactionRepository, new ObjectMapper());
    }

    @Test
    void consume_orderConfirmedWithItems_doesNotReserveStockAgain() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        String payload = "{\"eventType\":\"ORDER_CONFIRMED\",\"orderId\":\"" + orderId + "\","
                + "\"userId\":\"buyer@example.com\","
                + "\"items\":[{\"productId\":\"" + productId + "\",\"variantId\":\"" + variantId + "\",\"qty\":1}]}";

        consumer.consume(payload);

        verifyNoInteractions(inventoryService, eventPublisher, transactionRepository);
    }
}
