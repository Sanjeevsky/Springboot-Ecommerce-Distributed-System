package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.InventoryFeignClient;
import com.sanjeevsky.orderservice.model.InventoryStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class InventoryFeignClientFallback implements FallbackFactory<InventoryFeignClient> {

    @Override
    public InventoryFeignClient create(Throwable cause) {
        log.warn("Inventory service unavailable, skipping pre-order stock check", cause);
        return new InventoryFeignClient() {
            @Override
            public List<InventoryStock> getStockByProduct(UUID productId) {
                return null;
            }
        };
    }
}
