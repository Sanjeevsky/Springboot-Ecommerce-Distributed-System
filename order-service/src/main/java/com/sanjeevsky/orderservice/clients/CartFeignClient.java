package com.sanjeevsky.orderservice.clients;

import com.sanjeevsky.orderservice.clients.fallback.CartFeignClientFallback;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "shopping-cart-service", url = "${clients.cart.url:}", fallbackFactory = CartFeignClientFallback.class)
public interface CartFeignClient {

    @GetMapping("/cart-service/cart/checkout")
    CartSnapshot getCheckoutSnapshot(@RequestHeader("X-User") String userId);

    @DeleteMapping("/cart-service/cart/clear")
    void clearCart(@RequestHeader("X-User") String userId);
}
