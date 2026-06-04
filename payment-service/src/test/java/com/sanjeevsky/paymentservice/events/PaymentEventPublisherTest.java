package com.sanjeevsky.paymentservice.events;

import com.sanjeevsky.platform.events.PaymentConfirmedEvent;
import com.sanjeevsky.platform.events.PaymentInitiatedEvent;
import com.sanjeevsky.platform.events.PaymentRefundedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    private static final UUID PAYMENT_ID = UUID.fromString("c7699a6b-2f69-42b6-934f-27440cd90b72");
    private static final UUID ORDER_ID = UUID.fromString("cc312214-2424-48cb-9852-5cfc251d3630");
    private static final String USER_ID = "buyer@example.com";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publishPaymentInitiated_sendsToPaymentEventsWithOrderIdKey() {
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .paymentId(PAYMENT_ID)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .amount(199.0)
                .build();

        new PaymentEventPublisher(kafkaTemplate).publishPaymentInitiated(event);

        verify(kafkaTemplate).send(PaymentEventPublisher.PAYMENT_EVENTS_TOPIC, ORDER_ID.toString(), event);
    }

    @Test
    void publishPaymentConfirmed_sendsToPaymentEventsWithOrderIdKey() {
        PaymentConfirmedEvent event = PaymentConfirmedEvent.builder()
                .paymentId(PAYMENT_ID)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .amount(199.0)
                .build();

        new PaymentEventPublisher(kafkaTemplate).publishPaymentConfirmed(event);

        verify(kafkaTemplate).send(PaymentEventPublisher.PAYMENT_EVENTS_TOPIC, ORDER_ID.toString(), event);
    }

    @Test
    void publishPaymentRefunded_sendsToPaymentEventsWithOrderIdKey() {
        PaymentRefundedEvent event = PaymentRefundedEvent.builder()
                .paymentId(PAYMENT_ID)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .amount(199.0)
                .build();

        new PaymentEventPublisher(kafkaTemplate).publishPaymentRefunded(event);

        verify(kafkaTemplate).send(PaymentEventPublisher.PAYMENT_EVENTS_TOPIC, ORDER_ID.toString(), event);
    }
}
