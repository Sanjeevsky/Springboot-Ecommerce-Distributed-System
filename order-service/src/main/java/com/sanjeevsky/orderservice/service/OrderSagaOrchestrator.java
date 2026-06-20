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
import com.sanjeevsky.platform.events.OrderItemEvent;
import com.sanjeevsky.platform.events.StockReservationRequestedEvent;
import com.sanjeevsky.platform.model.order.OrderStatus;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central coordinator of the order checkout saga (orchestration style).
 *
 * <p>Forward flow: {@link #startSaga} -> {@link #onStockReserved} -> {@link #onPaymentConfirmed}.
 * Failure flow: {@link #onStockInsufficient} (fail fast, nothing reserved) or
 * {@link #onPaymentFailed} (compensate by releasing reserved stock and cancelling the order).
 *
 * <p>Each step is driven by a Kafka event/command and recorded in {@link SagaInstance}.
 * Every handler guards on the current {@link SagaStatus}, so redelivered Kafka messages are
 * no-ops (idempotency). The orchestrator owns the state machine; the participant services
 * (inventory, payment) only react to its commands and reply with events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final SagaInstanceRepository sagaRepository;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final CartFeignClient cartFeignClient;
    private final MeterRegistry meterRegistry;

    /**
     * Counts each saga that reaches a terminal state, tagged by outcome
     * (COMPLETED / COMPENSATED / FAILED). Drives the SagaCompensationRate alert and the
     * Studio/Grafana saga-outcome panel — a rising COMPENSATED rate means payments are
     * failing after stock was reserved.
     */
    private void recordOutcome(SagaStatus outcome) {
        meterRegistry.counter("order_saga_terminal_total", "outcome", outcome.name()).increment();
    }

    /**
     * Begins the saga: persists the saga log in {@code STARTED} and requests stock reservation
     * (step 1). The order is left {@code PENDING} until the saga completes or compensates.
     */
    @Transactional
    public SagaInstance startSaga(Order order, boolean simulatePaymentFailure) {
        SagaInstance saga = SagaInstance.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .status(SagaStatus.STARTED)
                .currentStep("REQUEST_STOCK_RESERVATION")
                .simulatePaymentFailure(simulatePaymentFailure)
                .build();
        SagaInstance saved = sagaRepository.save(saga);
        log.info("Saga STARTED for orderId={} (simulatePaymentFailure={})", order.getId(), simulatePaymentFailure);

        eventPublisher.publishStockReservationRequested(StockReservationRequestedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .items(toItemEvents(order.getOrderItems()))
                .build());
        return saved;
    }

    /** Step 1 succeeded -> issue the payment charge command (step 2). */
    @Transactional
    public void onStockReserved(UUID orderId) {
        sagaRepository.findByOrderId(orderId).ifPresentOrElse(saga -> {
            if (saga.getStatus() != SagaStatus.STARTED) {
                log.info("Ignoring StockReserved for orderId={}; saga already {}", orderId, saga.getStatus());
                return;
            }
            saga.setStatus(SagaStatus.STOCK_RESERVED);
            saga.setCurrentStep("CHARGE_PAYMENT");
            sagaRepository.save(saga);
            log.info("Saga STOCK_RESERVED for orderId={}, requesting payment charge", orderId);

            Order order = requireOrder(orderId);
            eventPublisher.publishChargePayment(ChargePaymentCommand.builder()
                    .orderId(orderId)
                    .userId(order.getUserId())
                    .amount(order.getOrderTotal())
                    .idempotencyKey("saga-charge:" + orderId)
                    .simulateFailure(saga.isSimulatePaymentFailure())
                    .build());
        }, () -> log.warn("No saga found for orderId={} on StockReserved", orderId));
    }

    /** Step 1 failed -> fail fast. Nothing was reserved, so no compensation is required. */
    @Transactional
    public void onStockInsufficient(UUID orderId, String reason) {
        sagaRepository.findByOrderId(orderId).ifPresentOrElse(saga -> {
            if (saga.getStatus() == SagaStatus.FAILED || isTerminal(saga.getStatus())) {
                log.info("Ignoring StockInsufficient for orderId={}; saga already {}", orderId, saga.getStatus());
                return;
            }
            saga.setStatus(SagaStatus.FAILED);
            saga.setCurrentStep("STOCK_INSUFFICIENT");
            saga.setLastError(reason);
            sagaRepository.save(saga);
            cancelOrder(orderId, reason);
            recordOutcome(SagaStatus.FAILED);
            log.info("Saga FAILED (insufficient stock) for orderId={}: {}", orderId, reason);
        }, () -> log.warn("No saga found for orderId={} on StockInsufficient", orderId));
    }

    /** Step 2 succeeded -> confirm the order, clear the cart, complete the saga (step 3). */
    @Transactional
    public void onPaymentConfirmed(UUID orderId, UUID paymentId) {
        sagaRepository.findByOrderId(orderId).ifPresentOrElse(saga -> {
            if (saga.getStatus() != SagaStatus.STOCK_RESERVED) {
                log.info("Ignoring PaymentConfirmed for orderId={}; saga already {}", orderId, saga.getStatus());
                return;
            }
            saga.setStatus(SagaStatus.PAYMENT_CONFIRMED);
            saga.setCurrentStep("CONFIRM_ORDER");
            sagaRepository.save(saga);

            Order order = requireOrder(orderId);
            order.setPaymentId(paymentId);
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            clearCartQuietly(order.getUserId());

            saga.setStatus(SagaStatus.COMPLETED);
            saga.setCurrentStep("COMPLETED");
            sagaRepository.save(saga);
            recordOutcome(SagaStatus.COMPLETED);

            eventPublisher.publishOrderConfirmed(OrderConfirmedEvent.builder()
                    .orderId(orderId)
                    .userId(order.getUserId())
                    .totalAmount(order.getOrderTotal())
                    .items(toItemEvents(order.getOrderItems()))
                    .build());
            log.info("Saga COMPLETED for orderId={}, order CONFIRMED", orderId);
        }, () -> log.warn("No saga found for orderId={} on PaymentConfirmed", orderId));
    }

    /** Step 2 failed -> compensate step 1: cancel the order so inventory releases reserved stock. */
    @Transactional
    public void onPaymentFailed(UUID orderId, String reason) {
        sagaRepository.findByOrderId(orderId).ifPresentOrElse(saga -> {
            if (isTerminal(saga.getStatus()) || saga.getStatus() == SagaStatus.COMPENSATING) {
                log.info("Ignoring PaymentFailed for orderId={}; saga already {}", orderId, saga.getStatus());
                return;
            }
            saga.setStatus(SagaStatus.COMPENSATING);
            saga.setCurrentStep("RELEASE_STOCK");
            saga.setLastError(reason);
            sagaRepository.save(saga);
            log.info("Saga COMPENSATING for orderId={} (payment failed): releasing reserved stock", orderId);

            // OrderCancelledEvent triggers inventory-service to release the reserved stock
            // (OrderEventConsumer.handleOrderCancelled) — this is the compensation of step 1.
            cancelOrder(orderId, reason);

            saga.setStatus(SagaStatus.COMPENSATED);
            saga.setCurrentStep("COMPENSATED");
            sagaRepository.save(saga);
            recordOutcome(SagaStatus.COMPENSATED);
            log.info("Saga COMPENSATED for orderId={}, order CANCELLED", orderId);
        }, () -> log.warn("No saga found for orderId={} on PaymentFailed", orderId));
    }

    private void cancelOrder(UUID orderId, String reason) {
        Order order = requireOrder(orderId);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
        }
        eventPublisher.publishOrderCancelled(OrderCancelledEvent.builder()
                .orderId(orderId)
                .userId(order.getUserId())
                .reason(reason)
                .build());
    }

    private void clearCartQuietly(String userId) {
        try {
            cartFeignClient.clearCart(userId);
        } catch (RuntimeException ex) {
            log.warn("Failed to clear cart for user={} after saga completion: {}", userId, ex.getMessage());
        }
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Saga references missing order: " + orderId));
    }

    private boolean isTerminal(SagaStatus status) {
        return status == SagaStatus.COMPLETED || status == SagaStatus.COMPENSATED || status == SagaStatus.FAILED;
    }

    private List<OrderItemEvent> toItemEvents(List<OrderItem> items) {
        return items.stream()
                .map(i -> OrderItemEvent.builder()
                        .productId(i.getProductId())
                        .variantId(i.getVariantId())
                        .productName(i.getProductName())
                        .unitPrice(i.getUnitPrice())
                        .qty(i.getQty())
                        .build())
                .collect(Collectors.toList());
    }
}
