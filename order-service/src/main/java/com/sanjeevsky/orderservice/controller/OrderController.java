package com.sanjeevsky.orderservice.controller;

import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.service.OrderService;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/order-service")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/order/{id}")
    public ResponseEntity<ApiResponse<Order>> getOrder(
            @RequestHeader("X-User") String userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderById(userId, id)));
    }

    @PostMapping("/order")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @RequestHeader("X-User") String userId,
            @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(userId, request.getAddressId());
        return new ResponseEntity<>(ApiResponse.ok("Order placed successfully", order), HttpStatus.CREATED);
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<Order>>> getOrders(
            @RequestHeader("X-User") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrdersByUser(userId)));
    }

    @PutMapping("/order/{id}/confirm")
    public ResponseEntity<ApiResponse<Order>> confirmOrder(
            @RequestHeader("X-User") String userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Order confirmed", orderService.confirmOrder(userId, id)));
    }

    @PutMapping("/order/{id}/cancel")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(
            @RequestHeader("X-User") String userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled", orderService.cancelOrder(userId, id)));
    }

    public static class CreateOrderRequest {
        private UUID addressId;

        public UUID getAddressId() { return addressId; }
        public void setAddressId(UUID addressId) { this.addressId = addressId; }
    }
}
