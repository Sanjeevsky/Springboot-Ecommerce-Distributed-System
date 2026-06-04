package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.InventoryFeignClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryFeignClientFallbackTest {

    private static final InventoryFeignClient FALLBACK = new InventoryFeignClientFallback()
            .create(null);

    @Test
    void getStockByProduct_returnsNullToAllowKafkaReservationFallback() {
        assertThat(FALLBACK.getStockByProduct(UUID.randomUUID())).isNull();
    }
}
