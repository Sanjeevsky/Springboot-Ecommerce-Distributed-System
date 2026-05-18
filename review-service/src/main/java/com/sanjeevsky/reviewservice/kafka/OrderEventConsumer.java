package com.sanjeevsky.reviewservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "order-events", groupId = "review-group")
    public void consumeOrderEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            // Detect OrderConfirmedEvent: has totalAmount but no items and no reason
            boolean hasTotalAmount = node.has("totalAmount");
            boolean hasItems = node.has("items");
            boolean hasReason = node.has("reason");

            if (hasTotalAmount && !hasItems && !hasReason) {
                String orderId = node.has("orderId") ? node.get("orderId").asText() : "unknown";
                String userId = node.has("userId") ? node.get("userId").asText() : "unknown";
                log.info("OrderConfirmedEvent received — User {} is now eligible to review products from order {}",
                        userId, orderId);
            }
        } catch (Exception e) {
            log.warn("Failed to parse order event message: {}", e.getMessage());
        }
    }
}
