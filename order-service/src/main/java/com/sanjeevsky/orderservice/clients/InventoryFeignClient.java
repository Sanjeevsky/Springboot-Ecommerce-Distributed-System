package com.sanjeevsky.orderservice.clients;

import com.sanjeevsky.orderservice.clients.fallback.InventoryFeignClientFallback;
import com.sanjeevsky.orderservice.model.InventoryStock;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "inventory-service", url = "${clients.inventory.url:}", fallbackFactory = InventoryFeignClientFallback.class)
public interface InventoryFeignClient {

    @GetMapping("/inventory-service/stock/{productId}")
    List<InventoryStock> getStockByProduct(@PathVariable("productId") UUID productId);
}
