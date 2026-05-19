package com.sanjeevsky.wishlistservice.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "shopping-cart-service", url = "${clients.cart.url:}")
public interface CartFeignClient {

    @PostMapping("/cart-service/cart/add")
    void addItem(@RequestHeader("X-User") String userId, @RequestBody Map<String, Object> item);
}
