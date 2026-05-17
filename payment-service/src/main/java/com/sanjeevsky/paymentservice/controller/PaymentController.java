package com.sanjeevsky.paymentservice.controller;

import com.sanjeevsky.paymentservice.model.Payment;
import com.sanjeevsky.paymentservice.model.PaymentRequest;
import com.sanjeevsky.paymentservice.model.PaymentStatus;
import com.sanjeevsky.paymentservice.service.PaymentService;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/payment-service")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<Payment>> initiatePayment(@RequestBody PaymentRequest request) {
        log.info("Received initiate payment request for orderId: {}", request.getOrderId());
        return new ResponseEntity<>(ApiResponse.ok(paymentService.initiatePayment(request)), HttpStatus.CREATED);
    }

    @PutMapping("/confirm/{paymentId}")
    public ResponseEntity<ApiResponse<Payment>> confirmPayment(@PathVariable("paymentId") UUID paymentId) {
        log.info("Received confirm payment request for paymentId: {}", paymentId);
        return ResponseEntity.ok(ApiResponse.ok("Payment confirmed", paymentService.confirmPayment(paymentId)));
    }

    @PutMapping("/fail/{paymentId}")
    public ResponseEntity<ApiResponse<Payment>> failPayment(@PathVariable("paymentId") UUID paymentId) {
        log.info("Received fail payment request for paymentId: {}", paymentId);
        return ResponseEntity.ok(ApiResponse.ok("Payment marked failed", paymentService.failPayment(paymentId)));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<Payment>> getByPaymentId(@PathVariable("paymentId") UUID paymentId) {
        log.info("Received get payment request for paymentId: {}", paymentId);
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getByPaymentId(paymentId)));
    }

    @GetMapping("/status/{orderId}")
    public ResponseEntity<ApiResponse<PaymentStatus>> getStatusByOrderId(@PathVariable("orderId") UUID orderId) {
        log.info("Received get payment status request for orderId: {}", orderId);
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getStatusByOrderId(orderId)));
    }
}
