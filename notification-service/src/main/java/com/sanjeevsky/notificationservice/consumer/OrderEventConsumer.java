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
            String orderId = root.has("orderId") ? root.get("orderId").asText() : "unknown";

            Notification notification;

            if (root.has("items")) {
                // OrderPlacedEvent
                double totalAmount = root.has("totalAmount") ? root.get("totalAmount").asDouble() : 0.0;
                List<String> itemDescriptions = new ArrayList<>();
                for (JsonNode item : root.get("items")) {
                    String productName = item.has("productName") ? item.get("productName").asText() : "Unknown";
                    int qty = item.has("qty") ? item.get("qty").asInt() : 0;
                    itemDescriptions.add(productName + " x " + qty);
                }
                String itemsSummary = String.join(", ", itemDescriptions);

                notification = Notification.builder()
                        .userId(userId)
                        .type("ORDER_PLACED")
                        .subject("Order Placed #" + orderId)
                        .message(String.format(
                                "Your order of $%.2f has been placed. Items: %s", totalAmount, itemsSummary))
                        .build();

                log.info("OrderPlacedEvent processed for orderId={}, userId={}", orderId, userId);

            } else if (root.has("reason")) {
                // OrderCancelledEvent
                String reason = root.get("reason").asText();

                notification = Notification.builder()
                        .userId(userId)
                        .type("ORDER_CANCELLED")
                        .subject("Order Cancelled")
                        .message("Your order has been cancelled: " + reason)
                        .build();

                log.info("OrderCancelledEvent processed for orderId={}, userId={}", orderId, userId);

            } else {
                // OrderConfirmedEvent
                notification = Notification.builder()
                        .userId(userId)
                        .type("ORDER_CONFIRMED")
                        .subject("Order Confirmed")
                        .message("Your order has been confirmed.")
                        .build();

                log.info("OrderConfirmedEvent processed for orderId={}, userId={}", orderId, userId);
            }

            notificationRepository.save(notification);
            log.info("Notification saved for userId={}, type={}", userId, notification.getType());

        } catch (Exception e) {
            log.error("Failed to process order event payload: {}", payload, e);
        }
    }
}
