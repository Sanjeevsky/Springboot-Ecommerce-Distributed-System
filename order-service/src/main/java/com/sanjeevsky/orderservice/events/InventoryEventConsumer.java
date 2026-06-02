package com.sanjeevsky.orderservice.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.model.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory-events", groupId = "order-group")
    public void consume(String payload) {
        log.info("Received inventory event: {}", payload);
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.has("orderId")) {
                throw new IllegalArgumentException("Inventory event missing orderId");
            }
            UUID orderId = UUID.fromString(root.get("orderId").asText());
            String userId = root.has("userId") ? root.get("userId").asText() : null;

            if (root.has("availableQty")) {
                handleStockInsufficient(orderId, userId, root);
            } else if (root.has("items")) {
                handleStockReserved(orderId, userId);
            } else {
                throw new IllegalArgumentException("Unrecognised inventory event format for orderId=" + orderId);
            }
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", payload, e);
            throw new IllegalStateException("Failed to process inventory event", e);
        }
    }

    private void handleStockInsufficient(UUID orderId, String userId, JsonNode root) {
        UUID productId = root.has("productId") ? UUID.fromString(root.get("productId").asText()) : null;
        int available = root.has("availableQty") ? root.get("availableQty").asInt() : 0;
        int requested = root.has("requestedQty") ? root.get("requestedQty").asInt() : 0;
        log.warn("StockInsufficientEvent orderId={} productId={} available={} requested={}", orderId, productId, available, requested);

        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
                return;
            }
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.info("Order {} auto-cancelled due to insufficient stock", orderId);
            eventPublisher.publishOrderCancelled(OrderCancelledEvent.builder()
                    .orderId(orderId)
                    .userId(userId != null ? userId : order.getUserId())
                    .reason("Insufficient stock for product " + productId + " (available=" + available + ", requested=" + requested + ")")
                    .build());
        }, () -> log.warn("Order {} not found for StockInsufficientEvent", orderId));
    }

    private void handleStockReserved(UUID orderId, String userId) {
        log.info("StockReservedEvent orderId={}, stock reserved", orderId);
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if (order.getStatus() != OrderStatus.PENDING) {
                log.info("Order {} is already {}; stock reservation does not change status", orderId, order.getStatus());
                return;
            }
            log.info("Order {} remains PENDING until explicit confirmation", orderId);
        }, () -> log.warn("Order {} not found for StockReservedEvent", orderId));
    }
}
