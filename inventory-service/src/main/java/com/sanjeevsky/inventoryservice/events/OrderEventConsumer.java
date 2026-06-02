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
            String eventType = root.has("eventType") ? root.get("eventType").asText() : "";

            if ("ORDER_PLACED".equals(eventType) || (eventType.isEmpty() && root.has("items"))) {
                // OrderPlacedEvent
                handleOrderPlaced(objectMapper.treeToValue(root, OrderPlacedEvent.class));
            } else if ("ORDER_CANCELLED".equals(eventType) || (eventType.isEmpty() && root.has("reason"))) {
                // OrderCancelledEvent
                handleOrderCancelled(objectMapper.treeToValue(root, OrderCancelledEvent.class));
            } else if ("ORDER_CONFIRMED".equals(eventType)) {
                log.debug("Ignoring OrderConfirmedEvent for inventory reservation");
            } else {
                throw new IllegalArgumentException("Unknown order event format");
            }
        } catch (Exception e) {
            log.error("Error processing order event: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to process order event", e);
        }
    }

    private void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Handling OrderPlacedEvent for orderId={}", event.getOrderId());
        List<OrderItemEvent> items = event.getItems();

        if (event.getOrderId() == null || event.getUserId() == null || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("OrderPlacedEvent missing required fields");
        }

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
        if (orderId == null) {
            throw new IllegalArgumentException("OrderCancelledEvent missing orderId");
        }

        List<InventoryTransaction> transactions = transactionRepository.findAllByOrderId(orderId);

        for (InventoryTransaction tx : transactions) {
            if ("RESERVE".equals(tx.getType())) {
                Inventory inventory = inventoryService.getStockById(tx.getInventoryId());
                inventoryService.releaseStock(
                        orderId,
                        inventory.getProductId(),
                        inventory.getVariantId(),
                        tx.getQuantity()
                );
            }
        }
    }
}
