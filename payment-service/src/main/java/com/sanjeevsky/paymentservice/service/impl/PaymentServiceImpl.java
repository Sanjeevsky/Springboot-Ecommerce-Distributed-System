package com.sanjeevsky.paymentservice.service.impl;

import com.sanjeevsky.paymentservice.events.PaymentEventPublisher;
import com.sanjeevsky.paymentservice.exceptions.PaymentNotFoundException;
import com.sanjeevsky.paymentservice.model.Payment;
import com.sanjeevsky.paymentservice.model.PaymentRequest;
import com.sanjeevsky.paymentservice.model.PaymentStatus;
import com.sanjeevsky.paymentservice.repository.PaymentRepository;
import com.sanjeevsky.paymentservice.service.PaymentService;
import com.sanjeevsky.platform.events.PaymentConfirmedEvent;
import com.sanjeevsky.platform.events.PaymentInitiatedEvent;
import com.sanjeevsky.platform.events.PaymentRefundedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentEventPublisher eventPublisher;

    @Override
    public Payment initiatePayment(PaymentRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        log.info("Initiating payment for orderId: {}, userId: {}, idempotencyKey: {}",
                request.getOrderId(), request.getUserId(), idempotencyKey);

        if (idempotencyKey != null) {
            return paymentRepository.findByUserIdAndIdempotencyKey(request.getUserId(), idempotencyKey)
                    .map(existing -> {
                        log.info("Returning existing paymentId: {} for userId: {}, idempotencyKey: {}",
                                existing.getId(), request.getUserId(), idempotencyKey);
                        return existing;
                    })
                    .orElseGet(() -> createPayment(request, idempotencyKey));
        }

        return createPayment(request, null);
    }

    private Payment createPayment(PaymentRequest request, String idempotencyKey) {
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .idempotencyKey(idempotencyKey)
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
                .build();
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment initiated with paymentId: {}", savedPayment.getId());

        eventPublisher.publishPaymentInitiated(PaymentInitiatedEvent.builder()
                .paymentId(savedPayment.getId())
                .orderId(savedPayment.getOrderId())
                .userId(savedPayment.getUserId())
                .amount(savedPayment.getAmount())
                .build());

        return savedPayment;
    }

    @Override
    public Payment confirmPayment(UUID paymentId) {
        log.info("Confirming payment for paymentId: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Payment already confirmed for paymentId: {}", paymentId);
            return payment;
        }
        payment.setStatus(PaymentStatus.SUCCESS);
        Payment confirmed = paymentRepository.save(payment);

        eventPublisher.publishPaymentConfirmed(PaymentConfirmedEvent.builder()
                .paymentId(confirmed.getId())
                .orderId(confirmed.getOrderId())
                .userId(confirmed.getUserId())
                .amount(confirmed.getAmount())
                .build());

        return confirmed;
    }

    @Override
    public Payment failPayment(UUID paymentId) {
        log.info("Failing payment for paymentId: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Payment already failed for paymentId: {}", paymentId);
            return payment;
        }
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
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment already refunded for paymentId: {}", paymentId);
            return payment;
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        Payment refunded = paymentRepository.save(payment);

        eventPublisher.publishPaymentRefunded(PaymentRefundedEvent.builder()
                .paymentId(refunded.getId())
                .orderId(refunded.getOrderId())
                .userId(refunded.getUserId())
                .amount(refunded.getAmount())
                .build());

        return refunded;
    }

    @Override
    public PaymentStatus getStatusByOrderId(UUID orderId) {
        log.info("Fetching payment status for orderId: {}", orderId);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for orderId: " + orderId));
        return payment.getStatus();
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
