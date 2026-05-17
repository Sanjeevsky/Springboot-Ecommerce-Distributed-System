package com.sanjeevsky.customerservice.clients.fallback;

import com.sanjeevsky.customerservice.clients.PaymentFeignClient;

import java.util.UUID;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import com.sanjeevsky.platform.model.payment.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentFeignClientFallback implements PaymentFeignClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentFeignClientFallback.class);

    @Override
    public PaymentResponse initiatePayment(PaymentRequest request) {
        log.warn("payment-service unavailable; returning FAILED payment for order {}", request.getOrderId());
        return PaymentResponse.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency("INR")
                .status(PaymentStatus.FAILED)
                .build();
    }

    @Override
    public PaymentResponse confirmPayment(UUID paymentId) {
        log.warn("payment-service unavailable; cannot confirm payment {}", paymentId);
        return PaymentResponse.builder().status(PaymentStatus.FAILED).build();
    }

    @Override
    public PaymentResponse refundPayment(UUID paymentId) {
        log.warn("payment-service unavailable; cannot refund payment {}", paymentId);
        return PaymentResponse.builder().status(PaymentStatus.FAILED).build();
    }
}
