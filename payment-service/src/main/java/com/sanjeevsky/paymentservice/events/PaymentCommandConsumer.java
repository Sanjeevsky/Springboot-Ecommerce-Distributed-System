package com.sanjeevsky.paymentservice.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.paymentservice.service.PaymentService;
import com.sanjeevsky.platform.events.ChargePaymentCommand;
import com.sanjeevsky.platform.events.RefundPaymentCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Saga participant: consumes commands issued by the order-service orchestrator on the
 * {@code payment-commands} topic and replies on {@code payment-events}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCommandConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-commands", groupId = "payment-group")
    public void consume(String message) {
        log.info("Received payment command: {}", message);
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.has("eventType") ? root.get("eventType").asText() : "";

            if ("CHARGE_PAYMENT".equals(eventType)) {
                paymentService.charge(objectMapper.treeToValue(root, ChargePaymentCommand.class));
            } else if ("REFUND_PAYMENT".equals(eventType)) {
                RefundPaymentCommand command = objectMapper.treeToValue(root, RefundPaymentCommand.class);
                if (command.getPaymentId() == null) {
                    throw new IllegalArgumentException("RefundPaymentCommand missing paymentId");
                }
                paymentService.refundPayment(command.getPaymentId());
            } else {
                throw new IllegalArgumentException("Unknown payment command eventType=" + eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process payment command: {}", message, e);
            throw new IllegalStateException("Failed to process payment command", e);
        }
    }
}
