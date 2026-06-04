package com.sanjeevsky.orderservice.events;

import com.sanjeevsky.orderservice.config.KafkaConfig;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.events.OrderConfirmedEvent;
import com.sanjeevsky.platform.events.OrderPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    private static final UUID ORDER_ID = UUID.fromString("1e39da61-ec21-426c-a1b2-64c9d1149451");
    private static final String USER_ID = "buyer@example.com";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publishOrderPlaced_sendsToOrderEventsWithOrderIdKey() {
        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .totalAmount(199.0)
                .items(List.of())
                .build();

        new OrderEventPublisher(kafkaTemplate).publishOrderPlaced(event);

        verify(kafkaTemplate).send(KafkaConfig.ORDER_EVENTS_TOPIC, ORDER_ID.toString(), event);
    }

    @Test
    void publishOrderConfirmed_sendsToOrderEventsWithOrderIdKey() {
        OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .totalAmount(199.0)
                .items(List.of())
                .build();

        new OrderEventPublisher(kafkaTemplate).publishOrderConfirmed(event);

        verify(kafkaTemplate).send(KafkaConfig.ORDER_EVENTS_TOPIC, ORDER_ID.toString(), event);
    }

    @Test
    void publishOrderCancelled_sendsToOrderEventsWithOrderIdKey() {
        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .reason("Customer requested cancellation")
                .build();

        new OrderEventPublisher(kafkaTemplate).publishOrderCancelled(event);

        verify(kafkaTemplate).send(KafkaConfig.ORDER_EVENTS_TOPIC, ORDER_ID.toString(), event);
    }
}
