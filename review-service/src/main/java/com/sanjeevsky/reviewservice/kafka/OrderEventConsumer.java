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

            if (!root.has("items")) {
                // Not an OrderPlacedEvent — confirmed/cancelled events carry no item list
                return;
            }

            UUID orderId = root.has("orderId") ? UUID.fromString(root.get("orderId").asText()) : null;
            String userId = root.has("userId") ? root.get("userId").asText() : null;

            if (orderId == null || userId == null) {
                log.warn("OrderPlacedEvent missing orderId or userId, skipping eligibility recording");
                return;
            }

            for (JsonNode item : root.get("items")) {
                if (!item.has("productId")) continue;
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
            log.warn("Failed to process order event for review eligibility: {}", e.getMessage());
        }
    }
}
