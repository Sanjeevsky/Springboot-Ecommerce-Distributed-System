package com.sanjeevsky.shoppingcartservice.clients.fallback;

import com.sanjeevsky.shoppingcartservice.clients.CatalogFeignClient;
import com.sanjeevsky.shoppingcartservice.exceptions.CatalogUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogFeignClientFallbackTest {

    private static final CatalogFeignClient FALLBACK = new CatalogFeignClientFallback();

    @Test
    void getProduct_throwsCatalogUnavailable() {
        assertThatThrownBy(() -> FALLBACK.getProduct(UUID.randomUUID()))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessageContaining("Catalog service is temporarily unavailable");
    }
}
