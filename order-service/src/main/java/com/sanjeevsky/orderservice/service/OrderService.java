package com.sanjeevsky.orderservice.service;

import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.model.SagaInstance;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    Order getOrderById(String userId, UUID id);
    Order createOrder(String userId, UUID addressId, String couponCode);
    Order createOrder(String userId, UUID addressId, String couponCode, String idempotencyKey);

    /**
     * Starts the orchestration-based saga checkout. Returns the persisted {@code PENDING} order
     * immediately (HTTP 202); the saga advances asynchronously over Kafka.
     */
    Order createOrderSaga(String userId, UUID addressId, String couponCode, String idempotencyKey, boolean simulatePaymentFailure);

    SagaInstance getSaga(String userId, UUID orderId);

    List<Order> getOrdersByUser(String userId);
    Order confirmOrder(String userId, UUID orderId);
    Order cancelOrder(String userId, UUID orderId);
}
