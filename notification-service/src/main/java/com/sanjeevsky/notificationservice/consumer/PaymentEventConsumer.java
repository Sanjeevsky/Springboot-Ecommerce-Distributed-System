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

            if (!root.has("paymentId")) {
                throw new IllegalArgumentException("Payment event missing paymentId");
            }

            String paymentId = root.get("paymentId").asText();
            String userId = root.has("userId") ? root.get("userId").asText() : "unknown";
            String orderId = root.has("orderId") ? root.get("orderId").asText() : "unknown";
            String eventType = root.has("eventType") ? root.get("eventType").asText() : "PAYMENT_PROCESSED";
            double amount = root.has("amount") ? root.get("amount").asDouble() : 0.0;
            String eventKey = "payment:" + paymentId + ":" + eventType;

            if (notificationRepository.existsByEventKey(eventKey)) {
                log.info("Skipping duplicate payment notification eventKey={}", eventKey);
                return;
            }

            Notification notification = Notification.builder()
                    .userId(userId)
                    .eventKey(eventKey)
                    .type(eventType)
                    .subject(paymentSubject(eventType))
                    .message(String.format("%s for order #%s", paymentMessage(eventType), orderId))
                    .build();

            notificationRepository.save(notification);
            log.info("Payment notification saved for userId={}, orderId={}, paymentId={}, amount={}, type={}",
                    userId, orderId, paymentId, amount, eventType);

        } catch (Exception e) {
            log.error("Failed to process payment event payload: {}", payload, e);
            throw new IllegalStateException("Failed to process payment event payload", e);
        }
    }

    private String paymentSubject(String eventType) {
        switch (eventType) {
            case "PAYMENT_INITIATED":
                return "Payment Initiated";
            case "PAYMENT_CONFIRMED":
                return "Payment Confirmed";
            case "PAYMENT_REFUNDED":
                return "Payment Refunded";
            default:
                return "Payment Processed";
        }
    }

    private String paymentMessage(String eventType) {
        switch (eventType) {
            case "PAYMENT_INITIATED":
                return "Payment initiated";
            case "PAYMENT_CONFIRMED":
                return "Payment confirmed";
            case "PAYMENT_REFUNDED":
                return "Payment refunded";
            default:
                return "Payment processed";
        }
    }
}
