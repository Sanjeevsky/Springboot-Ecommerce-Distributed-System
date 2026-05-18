package com.sanjeevsky.orderservice.clients;

import com.sanjeevsky.orderservice.clients.fallback.CartFeignClientFallback;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "shopping-cart-service", fallbackFactory = CartFeignClientFallback.class)
public interface CartFeignClient {

    @GetMapping("/shopping-cart-service/cart/checkout")
    CartSnapshot getCheckoutSnapshot(@RequestHeader("X-User") String userId);

    @DeleteMapping("/shopping-cart-service/cart")
    void clearCart(@RequestHeader("X-User") String userId);
}
