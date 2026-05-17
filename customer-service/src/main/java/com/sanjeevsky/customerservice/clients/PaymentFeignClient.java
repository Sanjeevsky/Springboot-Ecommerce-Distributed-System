package com.sanjeevsky.customerservice.clients;

import com.sanjeevsky.customerservice.clients.fallback.PaymentFeignClientFallback;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "payment-service", fallback = PaymentFeignClientFallback.class)
public interface PaymentFeignClient {

    @PostMapping("/payment-service/initiate")
    PaymentResponse initiatePayment(@RequestBody PaymentRequest request);

    @PutMapping("/payment-service/confirm/{paymentId}")
    PaymentResponse confirmPayment(@PathVariable("paymentId") UUID paymentId);

    @PutMapping("/payment-service/refund/{paymentId}")
    PaymentResponse refundPayment(@PathVariable("paymentId") UUID paymentId);
}
