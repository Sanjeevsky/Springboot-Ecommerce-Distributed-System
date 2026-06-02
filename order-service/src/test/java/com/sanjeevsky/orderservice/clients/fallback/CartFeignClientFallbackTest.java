package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.CartFeignClient;
import com.sanjeevsky.orderservice.exceptions.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartFeignClientFallbackTest {

    private static final CartFeignClient FALLBACK = new CartFeignClientFallback()
            .create(null);

    @Test
    void getCheckoutSnapshot_throwsServiceUnavailable() {
        assertThatThrownBy(() -> FALLBACK.getCheckoutSnapshot("user@example.com"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("checkout");
    }

    @Test
    void clearCart_throwsServiceUnavailable() {
        assertThatThrownBy(() -> FALLBACK.clearCart("user@example.com"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("cart clear");
    }
}
