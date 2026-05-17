package com.sanjeevsky.paymentservice.controller;

import com.sanjeevsky.paymentservice.model.Payment;
import com.sanjeevsky.paymentservice.model.PaymentRequest;
import com.sanjeevsky.paymentservice.model.PaymentStatus;
import com.sanjeevsky.paymentservice.service.PaymentService;
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
    public ResponseEntity<Payment> initiatePayment(@RequestBody PaymentRequest request) {
        log.info("Received initiate payment request for orderId: {}", request.getOrderId());
        return new ResponseEntity<>(paymentService.initiatePayment(request), HttpStatus.CREATED);
    }

    @PutMapping("/confirm/{paymentId}")
    public ResponseEntity<Payment> confirmPayment(@PathVariable("paymentId") UUID paymentId) {
        log.info("Received confirm payment request for paymentId: {}", paymentId);
        return new ResponseEntity<>(paymentService.confirmPayment(paymentId), HttpStatus.OK);
    }

    @PutMapping("/fail/{paymentId}")
    public ResponseEntity<Payment> failPayment(@PathVariable("paymentId") UUID paymentId) {
        log.info("Received fail payment request for paymentId: {}", paymentId);
        return new ResponseEntity<>(paymentService.failPayment(paymentId), HttpStatus.OK);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getByPaymentId(@PathVariable("paymentId") UUID paymentId) {
        log.info("Received get payment request for paymentId: {}", paymentId);
        return new ResponseEntity<>(paymentService.getByPaymentId(paymentId), HttpStatus.OK);
    }

    @GetMapping("/status/{orderId}")
    public ResponseEntity<PaymentStatus> getStatusByOrderId(@PathVariable("orderId") UUID orderId) {
        log.info("Received get payment status request for orderId: {}", orderId);
        return new ResponseEntity<>(paymentService.getStatusByOrderId(orderId), HttpStatus.OK);
    }
}
