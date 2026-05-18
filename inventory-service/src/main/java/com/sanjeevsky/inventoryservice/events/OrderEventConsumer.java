package com.sanjeevsky.inventoryservice.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.inventoryservice.exceptions.InsufficientStockException;
import com.sanjeevsky.inventoryservice.model.Inventory;
import com.sanjeevsky.inventoryservice.model.InventoryTransaction;
import com.sanjeevsky.inventoryservice.repository.InventoryTransactionRepository;
import com.sanjeevsky.inventoryservice.service.InventoryService;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.events.OrderItemEvent;
import com.sanjeevsky.platform.events.OrderPlacedEvent;
import com.sanjeevsky.platform.events.StockInsufficientEvent;
import com.sanjeevsky.platform.events.StockReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final InventoryEventPublisher eventPublisher;
    private final InventoryTransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "inventory-group")
    public void consume(String message) {
        log.info("Received order event message: {}", message);
        try {
            JsonNode root = objectMapper.readTree(message);

            if (root.has("items")) {
                // OrderPlacedEvent
                handleOrderPlaced(objectMapper.treeToValue(root, OrderPlacedEvent.class));
            } else if (root.has("reason")) {
                // OrderCancelledEvent
                handleOrderCancelled(objectMapper.treeToValue(root, OrderCancelledEvent.class));
            } else {
                log.warn("Unknown order event format, ignoring. Message: {}", message);
            }
        } catch (Exception e) {
            log.error("Error processing order event: {}", e.getMessage(), e);
        }
    }

    private void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Handling OrderPlacedEvent for orderId={}", event.getOrderId());
        List<OrderItemEvent> items = event.getItems();

        for (OrderItemEvent item : items) {
            try {
                inventoryService.reserveStock(
                        event.getOrderId(),
                        item.getProductId(),
                        item.getVariantId(),
                        item.getQty()
                );
            } catch (InsufficientStockException ex) {
                log.warn("Insufficient stock for productId={}, variantId={}: {}", item.getProductId(), item.getVariantId(), ex.getMessage());

                int available = 0;
                try {
                    available = inventoryService.getStock(item.getProductId(), item.getVariantId()).getAvailableQty();
                } catch (Exception ignored) {
                }

                StockInsufficientEvent insufficientEvent = StockInsufficientEvent.builder()
                        .orderId(event.getOrderId())
                        .userId(event.getUserId())
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .availableQty(available)
                        .requestedQty(item.getQty())
                        .build();
                eventPublisher.publishStockInsufficient(insufficientEvent);
                return;
            }
        }

        // All items reserved successfully
        StockReservedEvent reservedEvent = StockReservedEvent.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .items(items)
                .build();
        eventPublisher.publishStockReserved(reservedEvent);
        log.info("StockReservedEvent published for orderId={}", event.getOrderId());
    }

    private void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Handling OrderCancelledEvent for orderId={}", event.getOrderId());
        UUID orderId = event.getOrderId();

        List<InventoryTransaction> transactions = transactionRepository.findAllByOrderId(orderId);

        for (InventoryTransaction tx : transactions) {
            if ("RESERVE".equals(tx.getType())) {
                try {
                    Inventory inventory = inventoryService.getStockById(tx.getInventoryId());
                    inventoryService.releaseStock(
                            orderId,
                            inventory.getProductId(),
                            inventory.getVariantId(),
                            tx.getQuantity()
                    );
                } catch (Exception e) {
                    log.error("Error releasing stock for inventoryId={}: {}", tx.getInventoryId(), e.getMessage(), e);
                }
            }
        }
    }
}
