package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.CartFeignClient;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CartFeignClientFallback implements FallbackFactory<CartFeignClient> {

    @Override
    public CartFeignClient create(Throwable cause) {
        log.warn("Cart service fallback triggered", cause);
        return new CartFeignClient() {
            @Override
            public CartSnapshot getCheckoutSnapshot(String userId) {
                return new CartSnapshot();
            }

            @Override
            public void clearCart(String userId) {
            }
        };
    }
}
