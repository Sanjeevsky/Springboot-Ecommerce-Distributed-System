package com.sanjeevsky.apigateway.config;

import com.sanjeevsky.apigateway.filter.AuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class GatewayConfig {

    @Autowired
    AuthenticationFilter filter;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service", r -> r.path("/users/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://user-service"))

                .route("auth-service", r -> r.path("/auth-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://auth-service"))
                .route("customer-service", r -> r.path("/customer-service/**")
                        .filters(f -> f.filter(filter))
                        .uri("lb://customer-service"))
                .build();
    }

}