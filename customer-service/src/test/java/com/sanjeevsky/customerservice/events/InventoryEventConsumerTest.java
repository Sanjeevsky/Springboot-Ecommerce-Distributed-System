package com.sanjeevsky.customerservice.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.customerservice.model.Order;
import com.sanjeevsky.customerservice.repository.OrderRepository;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.events.OrderConfirmedEvent;
import com.sanjeevsky.platform.model.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryEventConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private InventoryEventConsumer consumer;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String USER_ID = "buyer@example.com";

    private Order pendingOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUserId(USER_ID);
        order.setStatus(OrderStatus.PENDING);
        order.setOrderTotal(500.0);
        return order;
    }

    // ─── StockInsufficientEvent ───────────────────────────────────────────────

    @Test
    void consume_stockInsufficient_cancelsPendingOrder() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"productId\":\"" + PRODUCT_ID + "\",\"availableQty\":2,\"requestedQty\":5}";
        Order order = pendingOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        consumer.consume(payload);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    @Test
    void consume_stockInsufficient_publishesOrderCancelledEvent() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"productId\":\"" + PRODUCT_ID + "\",\"availableQty\":0,\"requestedQty\":3}";
        Order order = pendingOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        consumer.consume(payload);

        ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(eventPublisher).publishOrderCancelled(captor.capture());
        OrderCancelledEvent event = captor.getValue();
        assertThat(event.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(event.getUserId()).isEqualTo(USER_ID);
        assertThat(event.getReason()).contains("Insufficient stock");
    }

    @Test
    void consume_stockInsufficient_orderAlreadyCancelled_skips() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"productId\":\"" + PRODUCT_ID + "\",\"availableQty\":0,\"requestedQty\":1}";
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        consumer.consume(payload);

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderCancelled(any());
    }

    @Test
    void consume_stockInsufficient_orderNotFound_logsAndContinues() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"productId\":\"" + PRODUCT_ID + "\",\"availableQty\":0,\"requestedQty\":1}";
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        consumer.consume(payload);

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderCancelled(any());
    }

    // ─── StockReservedEvent ───────────────────────────────────────────────────

    @Test
    void consume_stockReserved_confirmsOrder() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"items\":[{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":2}]}";
        Order order = pendingOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        consumer.consume(payload);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).save(order);
    }

    @Test
    void consume_stockReserved_publishesOrderConfirmedEvent() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"items\":[{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":1}]}";
        Order order = pendingOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        consumer.consume(payload);

        ArgumentCaptor<OrderConfirmedEvent> captor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(eventPublisher).publishOrderConfirmed(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void consume_stockReserved_orderNotPending_skipsConfirmation() {
        String payload = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID + "\","
                + "\"items\":[{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":1}]}";
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        consumer.consume(payload);

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderConfirmed(any());
    }

    // ─── error handling ───────────────────────────────────────────────────────

    @Test
    void consume_invalidJson_doesNotThrow() {
        consumer.consume("not-json");

        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void consume_missingOrderId_skipsProcessing() {
        consumer.consume("{\"userId\":\"" + USER_ID + "\",\"availableQty\":0,\"requestedQty\":1}");

        verifyNoInteractions(orderRepository, eventPublisher);
    }
}
