package com.sanjeevsky.notificationservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.notificationservice.model.Notification;
import com.sanjeevsky.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "notification-group")
    public void consume(String payload) {
        log.info("Received payment event payload: {}", payload);
        try {
            JsonNode root = objectMapper.readTree(payload);

            String userId = root.has("userId") ? root.get("userId").asText() : "unknown";
            String orderId = root.has("orderId") ? root.get("orderId").asText() : "unknown";
            double amount = root.has("amount") ? root.get("amount").asDouble() : 0.0;

            Notification notification = Notification.builder()
                    .userId(userId)
                    .type("PAYMENT_PROCESSED")
                    .subject("Payment Processed")
                    .message(String.format("Payment processed for order #%s", orderId))
                    .build();

            notificationRepository.save(notification);
            log.info("Payment notification saved for userId={}, orderId={}, amount={}", userId, orderId, amount);

        } catch (Exception e) {
            log.error("Failed to process payment event payload: {}", payload, e);
        }
    }
}
