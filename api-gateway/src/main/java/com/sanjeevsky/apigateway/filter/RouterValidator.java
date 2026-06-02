package com.sanjeevsky.apigateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouterValidator {

    private static final List<Predicate<String>> OPEN_API_ENDPOINTS = List.of(
            path -> path.equals("/auth-service/signup"),
            path -> path.equals("/auth-service/login"),
            path -> path.equals("/catalog-service/product/list"),
            path -> path.equals("/catalog-service/product/search"),
            path -> path.startsWith("/catalog-service/product/getProduct/")
    );

    public Predicate<ServerHttpRequest> isSecured =
            request -> OPEN_API_ENDPOINTS
                    .stream()
                    .noneMatch(openEndpoint -> openEndpoint.test(request.getURI().getPath()));

}
