package com.sanjeevsky.orderservice.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.orderservice.repository.SagaInstanceRepository;
import com.sanjeevsky.orderservice.service.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Saga reply listener: consumes payment outcomes from the {@code payment-events} topic and
 * advances or compensates the saga. Only orders that have a {@link com.sanjeevsky.orderservice.model.SagaInstance}
 * are handled here; legacy synchronous orders are ignored, keeping the two flows isolated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderSagaOrchestrator orchestrator;
    private final SagaInstanceRepository sagaRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "order-payment-group")
    public void consume(String payload) {
        log.info("Received payment event: {}", payload);
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.has("eventType") ? root.get("eventType").asText() : "";
            if (!"PAYMENT_CONFIRMED".equals(eventType) && !"PAYMENT_FAILED".equals(eventType)) {
                log.debug("Ignoring payment event type={}", eventType);
                return;
            }
            if (!root.has("orderId")) {
                throw new IllegalArgumentException("Payment event missing orderId");
            }
            UUID orderId = UUID.fromString(root.get("orderId").asText());

            // Only orchestrated (saga) orders are driven here.
            if (sagaRepository.findByOrderId(orderId).isEmpty()) {
                log.debug("No saga for orderId={}, ignoring {} (legacy flow)", orderId, eventType);
                return;
            }

            if ("PAYMENT_CONFIRMED".equals(eventType)) {
                UUID paymentId = root.has("paymentId") ? UUID.fromString(root.get("paymentId").asText()) : null;
                orchestrator.onPaymentConfirmed(orderId, paymentId);
            } else {
                String reason = root.has("reason") ? root.get("reason").asText() : "Payment failed";
                orchestrator.onPaymentFailed(orderId, reason);
            }
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", payload, e);
            throw new IllegalStateException("Failed to process payment event", e);
        }
    }
}
