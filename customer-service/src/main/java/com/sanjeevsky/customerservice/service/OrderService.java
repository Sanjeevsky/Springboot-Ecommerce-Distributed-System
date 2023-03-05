package com.sanjeevsky.customerservice.service;

import com.sanjeevsky.customerservice.model.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderService {
    Order getOrderById(UUID user, UUID id);
}
