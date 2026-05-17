package com.sanjeevsky.customerservice.controller;

import com.sanjeevsky.customerservice.model.Order;
import com.sanjeevsky.customerservice.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.sanjeevsky.customerservice.utils.CommonConstants.UNAUTHORIZED_ACCESS;

@Slf4j
@RestController
@RequestMapping("/customer-service")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/order/{id}")
    public ResponseEntity<?> getOrder(
            @RequestHeader(name = "X-User") String userHeader,
            @PathVariable("id") UUID id) {
        if (userHeader == null || userHeader.isEmpty()) {
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        UUID user = UUID.fromString(userHeader);
        return new ResponseEntity<>(orderService.getOrderById(user, id), HttpStatus.OK);
    }

    @PostMapping("/order")
    public ResponseEntity<?> createOrder(
            @RequestHeader(name = "X-User") String userId,
            @RequestBody CreateOrderRequest request) {
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        Order order = orderService.createOrder(userId, request.getAddressId());
        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(
            @RequestHeader(name = "X-User") String userId) {
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body(UNAUTHORIZED_ACCESS);
        }
        return new ResponseEntity<>(orderService.getOrdersByUser(userId), HttpStatus.OK);
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
