package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.PaymentFeignClient;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentFeignClientFallback implements FallbackFactory<PaymentFeignClient> {

    @Override
    public PaymentFeignClient create(Throwable cause) {
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
