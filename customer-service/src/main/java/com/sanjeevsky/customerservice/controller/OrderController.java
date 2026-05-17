package com.sanjeevsky.customerservice.controller;

import com.sanjeevsky.customerservice.model.Order;
import com.sanjeevsky.customerservice.service.OrderService;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/customer-service")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/order/{id}")
    public ResponseEntity<ApiResponse<Order>> getOrder(
            @RequestHeader(name = "X-User") String userHeader,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderById(userHeader, id)));
    }

    @PostMapping("/order")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @RequestHeader(name = "X-User") String userId,
            @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(userId, request.getAddressId());
        return new ResponseEntity<>(ApiResponse.ok("Order placed successfully", order), HttpStatus.CREATED);
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<Order>>> getOrders(
            @RequestHeader(name = "X-User") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrdersByUser(userId)));
    }

    @PutMapping("/order/{id}/confirm")
    public ResponseEntity<ApiResponse<Order>> confirmOrder(
            @RequestHeader(name = "X-User") String userId,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Order confirmed", orderService.confirmOrder(userId, id)));
    }

    @PutMapping("/order/{id}/cancel")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(
            @RequestHeader(name = "X-User") String userId,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled", orderService.cancelOrder(userId, id)));
    }

    public static class CreateOrderRequest {
        private UUID addressId;

        public UUID getAddressId() {
            return addressId;
        }

        public void setAddressId(UUID addressId) {
            this.addressId = addressId;
        }
    }
}
