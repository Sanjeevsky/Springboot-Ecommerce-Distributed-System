package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.PaymentFeignClient;
import com.sanjeevsky.orderservice.exceptions.InvalidRequestException;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentFeignClientFallbackTest {

    private static final PaymentFeignClient FALLBACK = new PaymentFeignClientFallback()
            .create(null);

    @Test
    void initiatePayment_throwsInvalidRequest() {
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), "user@example.com", 100.0);

        assertThatThrownBy(() -> FALLBACK.initiatePayment(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("initiate payment");
    }

    @Test
    void confirmPayment_throwsInvalidRequest() {
        assertThatThrownBy(() -> FALLBACK.confirmPayment(UUID.randomUUID()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("confirm payment");
    }

    @Test
    void refundPayment_throwsInvalidRequest() {
        assertThatThrownBy(() -> FALLBACK.refundPayment(UUID.randomUUID()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("refund payment");
    }
}
