package com.sanjeevsky.apigateway.config;

import com.sanjeevsky.apigateway.filter.AuthenticationFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class GatewayConfig {

    private final AuthenticationFilter filter;

    public GatewayConfig(AuthenticationFilter filter) {
        this.filter = filter;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r.path("/auth-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://auth-service"))
                .route("catalog-service", r -> r.path("/catalog-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://catalog-service"))
                .route("cart-service", r -> r.path("/cart-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://shopping-cart-service"))
                .route("customer-service", r -> r.path("/customer-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://customer-service"))
                .route("payment-service", r -> r.path("/payment-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://payment-service"))
                .route("inventory-service", r -> r.path("/inventory-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://inventory-service"))
                .route("notification-service", r -> r.path("/notification-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://notification-service"))
                .route("order-service", r -> r.path("/order-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://order-service"))
                .route("coupon-service", r -> r.path("/coupon-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://coupon-service"))
                .route("review-service", r -> r.path("/review-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://review-service"))
                .route("wishlist-service", r -> r.path("/wishlist-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://wishlist-service"))
                .build();
    }

}
