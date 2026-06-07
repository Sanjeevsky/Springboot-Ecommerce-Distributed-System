package com.sanjeevsky.orderservice.service;

import com.sanjeevsky.orderservice.clients.CartFeignClient;
import com.sanjeevsky.orderservice.events.OrderEventPublisher;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.model.OrderItem;
import com.sanjeevsky.orderservice.model.SagaInstance;
import com.sanjeevsky.orderservice.model.SagaStatus;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.orderservice.repository.SagaInstanceRepository;
import com.sanjeevsky.platform.events.ChargePaymentCommand;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.events.OrderConfirmedEvent;
import com.sanjeevsky.platform.events.StockReservationRequestedEvent;
import com.sanjeevsky.platform.model.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    private static final String USER = "buyer@example.com";

    @Mock SagaInstanceRepository sagaRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderEventPublisher eventPublisher;
    @Mock CartFeignClient cartFeignClient;

    @InjectMocks OrderSagaOrchestrator orchestrator;

    private Order order(UUID id, OrderStatus status) {
        OrderItem item = OrderItem.builder()
                .productId(UUID.randomUUID())
                .variantId(UUID.randomUUID())
                .productName("Widget")
                .unitPrice(50.0)
                .qty(2)
                .build();
        return Order.builder()
                .id(id)
                .userId(USER)
                .status(status)
                .orderTotal(100.0)
                .orderItems(List.of(item))
                .build();
    }

    private SagaInstance saga(UUID orderId, SagaStatus status, boolean simulateFailure) {
        return SagaInstance.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(USER)
                .status(status)
                .simulatePaymentFailure(simulateFailure)
                .build();
    }

    @Test
    void startSaga_persistsStartedAndRequestsStockReservation() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING);
        when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        SagaInstance result = orchestrator.startSaga(order, false);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.STARTED);
        ArgumentCaptor<StockReservationRequestedEvent> captor = ArgumentCaptor.forClass(StockReservationRequestedEvent.class);
        verify(eventPublisher).publishStockReservationRequested(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(captor.getValue().getItems()).hasSize(1);
    }

    @Test
    void onStockReserved_advancesAndChargesPayment() {
        UUID orderId = UUID.randomUUID();
        when(sagaRepository.findByOrderId(orderId)).thenReturn(Optional.of(saga(orderId, SagaStatus.STARTED, true)));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, OrderStatus.PENDING)));

        orchestrator.onStockReserved(orderId);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.STOCK_RESERVED);

        ArgumentCaptor<ChargePaymentCommand> cmd = ArgumentCaptor.forClass(ChargePaymentCommand.class);
        verify(eventPublisher).publishChargePayment(cmd.capture());
        assertThat(cmd.getValue().getAmount()).isEqualTo(100.0);
        assertThat(cmd.getValue().isSimulateFailure()).isTrue();
    }

    @Test
    void onStockReserved_isIdempotent_whenAlreadyAdvanced() {
        UUID orderId = UUID.randomUUID();
        when(sagaRepository.findByOrderId(orderId)).thenReturn(Optional.of(saga(orderId, SagaStatus.STOCK_RESERVED, false)));

        orchestrator.onStockReserved(orderId);

        verify(eventPublisher, never()).publishChargePayment(any());
        verify(sagaRepository, never()).save(any());
    }

    @Test
    void onStockInsufficient_failsFastAndCancelsOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING);
        when(sagaRepository.findByOrderId(orderId)).thenReturn(Optional.of(saga(orderId, SagaStatus.STARTED, false)));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orchestrator.onStockInsufficient(orderId, "out of stock");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.FAILED);
        verify(eventPublisher).publishOrderCancelled(any(OrderCancelledEvent.class));
        verify(eventPublisher, never()).publishChargePayment(any());
    }

    @Test
    void onPaymentConfirmed_confirmsOrderClearsCartAndCompletes() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING);
        when(sagaRepository.findByOrderId(orderId)).thenReturn(Optional.of(saga(orderId, SagaStatus.STOCK_RESERVED, false)));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orchestrator.onPaymentConfirmed(orderId, paymentId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentId()).isEqualTo(paymentId);
        verify(cartFeignClient).clearCart(USER);
        verify(eventPublisher).publishOrderConfirmed(any(OrderConfirmedEvent.class));
        // last persisted saga state is COMPLETED
        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository, org.mockito.Mockito.atLeastOnce()).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void onPaymentFailed_compensatesByCancellingOrderToReleaseStock() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING);
        when(sagaRepository.findByOrderId(orderId)).thenReturn(Optional.of(saga(orderId, SagaStatus.STOCK_RESERVED, true)));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orchestrator.onPaymentFailed(orderId, "gateway declined");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<OrderCancelledEvent> cancel = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(eventPublisher).publishOrderCancelled(cancel.capture());
        assertThat(cancel.getValue().getReason()).contains("gateway declined");
        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository, org.mockito.Mockito.atLeastOnce()).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        verify(eventPublisher, never()).publishOrderConfirmed(any());
    }

    @Test
    void unknownOrder_isIgnoredGracefully() {
        UUID orderId = UUID.randomUUID();
        when(sagaRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        orchestrator.onStockReserved(orderId);
        orchestrator.onPaymentConfirmed(orderId, UUID.randomUUID());
        orchestrator.onPaymentFailed(orderId, "x");
        orchestrator.onStockInsufficient(orderId, "x");

        verify(eventPublisher, never()).publishChargePayment(any());
        verify(orderRepository, never()).save(any());
        assertThat(Collections.<String>emptyList()).isEmpty();
    }
}
