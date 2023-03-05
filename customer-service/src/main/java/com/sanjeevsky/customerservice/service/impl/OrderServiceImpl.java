package com.sanjeevsky.customerservice.service.impl;

import com.sanjeevsky.customerservice.model.Order;
import com.sanjeevsky.customerservice.repository.OrderRepository;
import com.sanjeevsky.customerservice.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public Order getOrderById(UUID user, UUID id) {
        Optional<Order> order = orderRepository.findByIdAndUserId(id,user);
        if (order.isEmpty()) throw new RuntimeException("Order Not Found");
        return order.get();
    }
}
