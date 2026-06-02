package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.PaymentFeignClient;
import com.sanjeevsky.orderservice.exceptions.ServiceUnavailableException;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentFeignClientFallbackTest {

    private static final PaymentFeignClient FALLBACK = new PaymentFeignClientFallback()
            .create(null);

    @Test
    void initiatePayment_throwsServiceUnavailable() {
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), "user@example.com", 100.0);

        assertThatThrownBy(() -> FALLBACK.initiatePayment(request))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("initiate payment");
    }

    @Test
    void confirmPayment_throwsServiceUnavailable() {
        assertThatThrownBy(() -> FALLBACK.confirmPayment(UUID.randomUUID()))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("confirm payment");
    }

    @Test
    void refundPayment_throwsServiceUnavailable() {
        assertThatThrownBy(() -> FALLBACK.refundPayment(UUID.randomUUID()))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("refund payment");
    }
}
