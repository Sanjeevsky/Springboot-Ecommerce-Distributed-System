package com.sanjeevsky.orderservice.clients;

import com.sanjeevsky.orderservice.clients.fallback.PaymentFeignClientFallback;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "payment-service", url = "${clients.payment.url:}", fallbackFactory = PaymentFeignClientFallback.class)
public interface PaymentFeignClient {

    @PostMapping("/payment-service/initiate/raw")
    PaymentResponse initiatePayment(@RequestBody PaymentRequest request);

    @PutMapping("/payment-service/confirm/{id}/raw")
    void confirmPayment(@PathVariable("id") UUID paymentId);

    @PutMapping("/payment-service/refund/{id}/raw")
    void refundPayment(@PathVariable("id") UUID paymentId);
}
