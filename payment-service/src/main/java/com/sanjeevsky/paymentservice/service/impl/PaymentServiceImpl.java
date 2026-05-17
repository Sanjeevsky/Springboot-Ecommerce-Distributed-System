package com.sanjeevsky.paymentservice.service.impl;

import com.sanjeevsky.paymentservice.exceptions.PaymentNotFoundException;
import com.sanjeevsky.paymentservice.model.Payment;
import com.sanjeevsky.paymentservice.model.PaymentRequest;
import com.sanjeevsky.paymentservice.model.PaymentStatus;
import com.sanjeevsky.paymentservice.repository.PaymentRepository;
import com.sanjeevsky.paymentservice.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Override
    public Payment initiatePayment(PaymentRequest request) {
        log.info("Initiating payment for orderId: {}, userId: {}", request.getOrderId(), request.getUserId());
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
                .build();
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment initiated with paymentId: {}", savedPayment.getId());
        return savedPayment;
    }

    @Override
    public Payment confirmPayment(UUID paymentId) {
        log.info("Confirming payment for paymentId: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        payment.setStatus(PaymentStatus.SUCCESS);
        return paymentRepository.save(payment);
    }

    @Override
    public Payment failPayment(UUID paymentId) {
        log.info("Failing payment for paymentId: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        payment.setStatus(PaymentStatus.FAILED);
        return paymentRepository.save(payment);
    }

    @Override
    public Payment getByPaymentId(UUID paymentId) {
        log.info("Fetching payment for paymentId: {}", paymentId);
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
    }

    @Override
    public Payment refundPayment(UUID paymentId) {
        log.info("Refunding payment for paymentId: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        payment.setStatus(PaymentStatus.REFUNDED);
        return paymentRepository.save(payment);
    }

    @Override
    public PaymentStatus getStatusByOrderId(UUID orderId) {
        log.info("Fetching payment status for orderId: {}", orderId);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for orderId: " + orderId));
        return payment.getStatus();
    }
}
