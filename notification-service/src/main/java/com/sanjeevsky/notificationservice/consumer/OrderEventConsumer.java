package com.sanjeevsky.notificationservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.notificationservice.model.Notification;
import com.sanjeevsky.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "notification-group")
    public void consume(String payload) {
        log.info("Received order event payload: {}", payload);
        try {
            JsonNode root = objectMapper.readTree(payload);

            String userId = root.has("userId") ? root.get("userId").asText() : "unknown";
            if (!root.has("orderId")) {
                throw new IllegalArgumentException("Order event missing orderId");
            }
            String orderId = root.get("orderId").asText();

            Notification notification;
            String eventType = root.has("eventType") ? root.get("eventType").asText() : inferLegacyEventType(root);

            if ("ORDER_PLACED".equals(eventType)) {
                // OrderPlacedEvent
                double totalAmount = root.has("totalAmount") ? root.get("totalAmount").asDouble() : 0.0;
                List<String> itemDescriptions = new ArrayList<>();
                JsonNode itemsNode = root.get("items");
                if (itemsNode != null && itemsNode.isArray()) {
                    for (JsonNode item : itemsNode) {
                        String productName = item.has("productName") ? item.get("productName").asText() : "Unknown";
                        int qty = item.has("qty") ? item.get("qty").asInt() : 0;
                        itemDescriptions.add(productName + " x " + qty);
                    }
                }
                String itemsSummary = String.join(", ", itemDescriptions);

                notification = Notification.builder()
                        .userId(userId)
                        .type(eventType)
                        .subject("Order Placed #" + orderId)
                        .message(String.format(
                                "Your order of $%.2f has been placed. Items: %s", totalAmount, itemsSummary))
                        .build();

                log.info("OrderPlacedEvent processed for orderId={}, userId={}", orderId, userId);

            } else if ("ORDER_CANCELLED".equals(eventType)) {
                // OrderCancelledEvent
                String reason = root.has("reason") ? root.get("reason").asText() : "No reason provided";

                notification = Notification.builder()
                        .userId(userId)
                        .type(eventType)
                        .subject("Order Cancelled")
                        .message("Your order has been cancelled: " + reason)
                        .build();

                log.info("OrderCancelledEvent processed for orderId={}, userId={}", orderId, userId);

            } else if ("ORDER_CONFIRMED".equals(eventType)) {
                // OrderConfirmedEvent
                notification = Notification.builder()
                        .userId(userId)
                        .type(eventType)
                        .subject("Order Confirmed")
                        .message("Your order has been confirmed.")
                        .build();

                log.info("OrderConfirmedEvent processed for orderId={}, userId={}", orderId, userId);
            } else {
                throw new IllegalArgumentException("Unknown order event type: " + eventType);
            }

            String eventKey = "order:" + orderId + ":" + eventType;
            if (notificationRepository.existsByEventKey(eventKey)) {
                log.info("Skipping duplicate order notification eventKey={}", eventKey);
                return;
            }

            notification.setEventKey(eventKey);
            notificationRepository.save(notification);
            log.info("Notification saved for userId={}, type={}", userId, notification.getType());

        } catch (Exception e) {
            log.error("Failed to process order event payload: {}", payload, e);
            throw new IllegalStateException("Failed to process order event payload", e);
        }
    }

    private String inferLegacyEventType(JsonNode root) {
        if (root.has("items")) {
            return "ORDER_PLACED";
        }
        if (root.has("reason")) {
            return "ORDER_CANCELLED";
        }
        return "ORDER_CONFIRMED";
    }
}
