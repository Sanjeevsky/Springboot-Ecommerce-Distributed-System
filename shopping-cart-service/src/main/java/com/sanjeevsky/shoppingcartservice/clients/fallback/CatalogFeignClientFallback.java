package com.sanjeevsky.shoppingcartservice.clients.fallback;

import com.sanjeevsky.platform.model.product.ProductResponse;
import com.sanjeevsky.shoppingcartservice.clients.CatalogFeignClient;
import com.sanjeevsky.shoppingcartservice.exceptions.CatalogUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class CatalogFeignClientFallback implements CatalogFeignClient {

    @Override
    public ProductResponse getProduct(UUID productId) {
        log.error("catalog-service unavailable; cannot fetch product id={}", productId);
        throw new CatalogUnavailableException("Catalog service is temporarily unavailable. Please try again later.");
    }
}
