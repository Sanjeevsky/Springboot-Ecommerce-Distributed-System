package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.CartFeignClient;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class CartFeignClientFallback implements FallbackFactory<CartFeignClient> {

    @Override
    public CartFeignClient create(Throwable cause) {
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
