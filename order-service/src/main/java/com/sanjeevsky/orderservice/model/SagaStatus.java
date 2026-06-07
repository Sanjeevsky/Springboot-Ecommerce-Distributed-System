package com.sanjeevsky.orderservice.model;

/**
 * Lifecycle of an orchestrated order saga, tracked in {@link SagaInstance}.
 *
 * <p>Happy path: {@code STARTED -> STOCK_RESERVED -> PAYMENT_CONFIRMED -> COMPLETED}.
 * Failure paths: {@code STARTED -> FAILED} (insufficient stock, nothing to undo) or
 * {@code STOCK_RESERVED -> COMPENSATING -> COMPENSATED} (payment failed, reserved stock released).
 *
 * <p>Kept separate from the shared {@code OrderStatus} so saga progress is visible without
 * widening an enum that other services depend on.
 */
public enum SagaStatus {
    STARTED,
    STOCK_RESERVED,
    PAYMENT_CONFIRMED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
