package com.sanjeevsky.apigateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterValidatorTest {

    private final RouterValidator routerValidator = new RouterValidator();

    @Test
    void permitsOnlyStandardOpenRoutes() {
        assertOpen("/auth-service/login");
        assertOpen("/auth-service/signup");
        assertOpen("/catalog-service/product/list");
        assertOpen("/catalog-service/product/search");
        assertOpen("/catalog-service/product/getProduct/5f8882c1-88a7-4ca6-898b-92f9417b5275");
    }

    @Test
    void securesStaleRawAndLookalikeRoutes() {
        assertSecured("/shopping-cart-service/cart");
        assertSecured("/catalog-service/product/list-extra");
        assertSecured("/review-service/product/5f8882c1-88a7-4ca6-898b-92f9417b5275");
        assertSecured("/coupon-service/active");
    }

    private void assertOpen(String path) {
        assertFalse(isSecured(path), path + " should be open");
    }

    private void assertSecured(String path) {
        assertTrue(isSecured(path), path + " should be secured");
    }

    private boolean isSecured(String path) {
        return routerValidator.isSecured.test(MockServerHttpRequest.get(path).build());
    }
}
