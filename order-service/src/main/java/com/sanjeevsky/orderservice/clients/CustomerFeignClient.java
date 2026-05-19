package com.sanjeevsky.orderservice.clients;

import com.sanjeevsky.orderservice.clients.fallback.CustomerFeignClientFallback;
import com.sanjeevsky.orderservice.model.AddressDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "customer-service", url = "${clients.customer.url:}", fallbackFactory = CustomerFeignClientFallback.class)
public interface CustomerFeignClient {

    @GetMapping("/customer-service/address/{id}")
    AddressDto getAddress(@RequestHeader("X-User") String userId, @PathVariable("id") UUID addressId);
}
