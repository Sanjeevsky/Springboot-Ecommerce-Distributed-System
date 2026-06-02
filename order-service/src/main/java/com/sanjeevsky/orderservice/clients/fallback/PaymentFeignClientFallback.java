package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.PaymentFeignClient;
import com.sanjeevsky.orderservice.exceptions.InvalidRequestException;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class PaymentFeignClientFallback implements FallbackFactory<PaymentFeignClient> {

    @Override
    public PaymentFeignClient create(Throwable cause) {
        log.warn("Payment service fallback triggered", cause);
        return new PaymentFeignClient() {
            @Override
            public PaymentResponse initiatePayment(PaymentRequest request) {
                throw paymentUnavailable("initiate payment");
            }

            @Override
            public void confirmPayment(UUID paymentId) {
                throw paymentUnavailable("confirm payment");
            }

            @Override
            public void refundPayment(UUID paymentId) {
                throw paymentUnavailable("refund payment");
            }
        };
    }

    private InvalidRequestException paymentUnavailable(String operation) {
        return new InvalidRequestException("Payment service unavailable during " + operation);
    }
}
