package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.CustomerFeignClient;
import com.sanjeevsky.orderservice.exceptions.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerFeignClientFallbackTest {

    private static final CustomerFeignClient FALLBACK = new CustomerFeignClientFallback()
            .create(null);

    @Test
    void getAddress_throwsServiceUnavailable() {
        assertThatThrownBy(() -> FALLBACK.getAddress("user@example.com", UUID.randomUUID()))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("address lookup");
    }
}
