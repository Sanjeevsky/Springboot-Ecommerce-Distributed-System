package com.sanjeevsky.orderservice.events;

import com.sanjeevsky.orderservice.config.KafkaConfig;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.events.OrderConfirmedEvent;
import com.sanjeevsky.platform.events.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing OrderPlacedEvent for orderId={}", event.getOrderId());
        kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, event.getOrderId().toString(), event);
    }

    public void publishOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Publishing OrderConfirmedEvent for orderId={}", event.getOrderId());
        kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, event.getOrderId().toString(), event);
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        log.info("Publishing OrderCancelledEvent for orderId={}", event.getOrderId());
        kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, event.getOrderId().toString(), event);
    }
}
