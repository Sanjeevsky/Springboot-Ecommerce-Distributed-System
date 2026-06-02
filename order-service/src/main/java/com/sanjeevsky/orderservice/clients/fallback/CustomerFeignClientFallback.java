package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.CustomerFeignClient;
import com.sanjeevsky.orderservice.exceptions.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class CustomerFeignClientFallback implements FallbackFactory<CustomerFeignClient> {

    @Override
    public CustomerFeignClient create(Throwable cause) {
        log.warn("Customer service fallback triggered", cause);
        return (userId, addressId) -> {
            throw new ServiceUnavailableException("Customer service unavailable during address lookup");
        };
    }
}
