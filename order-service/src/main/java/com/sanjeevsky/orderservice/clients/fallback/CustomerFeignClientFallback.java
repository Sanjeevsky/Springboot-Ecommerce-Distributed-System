package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.CustomerFeignClient;
import com.sanjeevsky.orderservice.model.AddressDto;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CustomerFeignClientFallback implements FallbackFactory<CustomerFeignClient> {

    @Override
    public CustomerFeignClient create(Throwable cause) {
        return (userId, addressId) -> null;
    }
}
