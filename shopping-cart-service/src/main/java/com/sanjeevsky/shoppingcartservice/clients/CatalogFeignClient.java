package com.sanjeevsky.shoppingcartservice.clients;

import com.sanjeevsky.platform.model.product.ProductResponse;
import com.sanjeevsky.shoppingcartservice.clients.fallback.CatalogFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "catalog-service", url = "${clients.catalog.url:}", fallback = CatalogFeignClientFallback.class)
public interface CatalogFeignClient {

    @GetMapping("/catalog-service/product/getProduct/{id}")
    ProductResponse getProduct(@PathVariable("id") UUID productId);
}
