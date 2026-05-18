package com.sanjeevsky.paymentservice.events;

import com.sanjeevsky.platform.events.PaymentConfirmedEvent;
import com.sanjeevsky.platform.events.PaymentInitiatedEvent;
import com.sanjeevsky.platform.events.PaymentRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        log.info("Publishing PaymentInitiatedEvent for paymentId={}", event.getPaymentId());
        kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event.getOrderId().toString(), event);
    }

    public void publishPaymentConfirmed(PaymentConfirmedEvent event) {
        log.info("Publishing PaymentConfirmedEvent for paymentId={}", event.getPaymentId());
        kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event.getOrderId().toString(), event);
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        log.info("Publishing PaymentRefundedEvent for paymentId={}", event.getPaymentId());
        kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event.getOrderId().toString(), event);
    }
}
