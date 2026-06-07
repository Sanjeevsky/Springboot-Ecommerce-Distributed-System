package com.sanjeevsky.orderservice.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.orderservice.repository.SagaInstanceRepository;
import com.sanjeevsky.orderservice.service.OrderSagaOrchestrator;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.model.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryEventConsumerTest {

    private static final String USER = "buyer@example.com";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @Mock
    private SagaInstanceRepository sagaRepository;

    @Mock
    private OrderSagaOrchestrator orchestrator;

    private InventoryEventConsumer consumer;

    @BeforeEach
    void setUp() {
        // sagaRepository returns Optional.empty() by default -> exercises the legacy (non-saga) path.
        consumer = new InventoryEventConsumer(orderRepository, eventPublisher, sagaRepository, orchestrator, new ObjectMapper());
    }

    @Test
    void stockReserved_leavesPendingOrderForExplicitConfirmation() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .userId(USER)
                .status(OrderStatus.PENDING)
                .orderTotal(100.0)
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        consumer.consume("{\"orderId\":\"" + orderId + "\",\"userId\":\"" + USER + "\",\"items\":[]}");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void stockInsufficient_cancelsPendingOrderAndPublishesCancellation() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .userId(USER)
                .status(OrderStatus.PENDING)
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        consumer.consume("{\"orderId\":\"" + orderId + "\",\"userId\":\"" + USER
                + "\",\"productId\":\"" + productId + "\",\"availableQty\":0,\"requestedQty\":2}");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(eventPublisher).publishOrderCancelled(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(USER);
        assertThat(eventCaptor.getValue().getReason()).contains("Insufficient stock");
    }
}
