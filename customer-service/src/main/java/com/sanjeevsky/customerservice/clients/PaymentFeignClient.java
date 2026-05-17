package com.sanjeevsky.customerservice.clients;

import com.sanjeevsky.customerservice.clients.fallback.PaymentFeignClientFallback;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", fallback = PaymentFeignClientFallback.class)
public interface PaymentFeignClient {

    @PostMapping("/payment-service/initiate")
    PaymentResponse initiatePayment(@RequestBody PaymentRequest request);
}
