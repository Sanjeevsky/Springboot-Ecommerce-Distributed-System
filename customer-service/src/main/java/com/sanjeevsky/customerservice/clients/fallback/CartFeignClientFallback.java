package com.sanjeevsky.customerservice.clients.fallback;

import com.sanjeevsky.customerservice.clients.CartFeignClient;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class CartFeignClientFallback implements CartFeignClient {

    private static final Logger log = LoggerFactory.getLogger(CartFeignClientFallback.class);

    @Override
    public CartSnapshot getCheckoutSnapshot(String userId) {
        log.warn("shopping-cart-service unavailable; returning empty snapshot for user {}", userId);
        return CartSnapshot.builder()
                .userId(userId)
                .items(Collections.emptyList())
                .totalAmount(0.0)
                .build();
    }

    @Override
    public void clearCart(String userId) {
        log.warn("shopping-cart-service unavailable; cart clear skipped for user {}", userId);
    }
}
