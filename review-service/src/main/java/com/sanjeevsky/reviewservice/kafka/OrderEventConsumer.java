package com.sanjeevsky.reviewservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.reviewservice.model.OrderEligibility;
import com.sanjeevsky.reviewservice.repository.OrderEligibilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderEligibilityRepository eligibilityRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "review-group")
    public void consumeOrderEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.has("eventType") ? root.get("eventType").asText() : "";

            if (!"ORDER_CONFIRMED".equals(eventType)) {
                // Review eligibility is granted only after payment-backed order confirmation.
                return;
            }

            UUID orderId = root.has("orderId") ? UUID.fromString(root.get("orderId").asText()) : null;
            String userId = root.has("userId") ? root.get("userId").asText() : null;

            if (orderId == null || userId == null) {
                throw new IllegalArgumentException("OrderConfirmedEvent missing orderId or userId");
            }
            if (!root.has("items") || !root.get("items").isArray() || root.get("items").size() == 0) {
                throw new IllegalArgumentException("OrderConfirmedEvent missing items");
            }

            for (JsonNode item : root.get("items")) {
                if (!item.has("productId")) {
                    throw new IllegalArgumentException("OrderConfirmedEvent item missing productId");
                }
                UUID productId = UUID.fromString(item.get("productId").asText());

                if (!eligibilityRepository.existsByUserIdAndProductId(userId, productId)) {
                    eligibilityRepository.save(OrderEligibility.builder()
                            .userId(userId)
                            .productId(productId)
                            .orderId(orderId)
                            .build());
                    log.info("Review eligibility recorded: userId={}, productId={}, orderId={}",
                            userId, productId, orderId);
                } else {
                    log.debug("Eligibility already exists for userId={}, productId={}", userId, productId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to process order event for review eligibility: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to process order event for review eligibility", e);
        }
    }
}
