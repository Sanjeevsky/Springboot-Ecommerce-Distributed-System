package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.PaymentFeignClient;
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
                return null;
            }

            @Override
            public void confirmPayment(UUID paymentId) {
            }

            @Override
            public void refundPayment(UUID paymentId) {
            }
        };
    }
}
