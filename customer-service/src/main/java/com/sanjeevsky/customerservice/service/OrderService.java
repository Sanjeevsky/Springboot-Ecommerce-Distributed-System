package com.sanjeevsky.customerservice.service;

import com.sanjeevsky.customerservice.model.Order;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    Order getOrderById(String user, UUID id);

    Order createOrder(String userId, UUID addressId);

    List<Order> getOrdersByUser(String userId);

    Order confirmOrder(String userId, UUID orderId);

    Order cancelOrder(String userId, UUID orderId);
}
