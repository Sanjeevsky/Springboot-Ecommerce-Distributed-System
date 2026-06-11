package com.sanjeevsky.apigateway.filter;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RefreshScope
@Component
public class AuthenticationFilter implements GatewayFilter {

    private final RouterValidator routerValidator;
    private final JwtUtil jwtUtil;

    public AuthenticationFilter(RouterValidator routerValidator, JwtUtil jwtUtil) {
        this.routerValidator = routerValidator;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (routerValidator.isSecured.test(request)) {
            final String token = this.getAuthHeader(request);
            if (token == null) {
                return this.onError(exchange, HttpStatus.UNAUTHORIZED);
            }

            try {
                Claims claims = jwtUtil.getAllClaimsFromToken(token);
                String role = claims.get("role", String.class);
                ServerHttpRequest mutated = request.mutate()
                        .headers(headers -> {
                            headers.set("X-User", claims.getSubject());
                            // Tokens issued before roles existed carry no claim — treat as customer.
                            headers.set("X-User-Role", role == null || role.isEmpty() ? "CUSTOMER" : role);
                        })
                        .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            } catch (Exception e) {
                return this.onError(exchange, HttpStatus.UNAUTHORIZED);
            }
        }
        // Open routes skip JWT validation, so identity headers must never pass through as-is.
        ServerHttpRequest cleaned = request.mutate()
                .headers(headers -> {
                    headers.remove("X-User");
                    headers.remove("X-User-Role");
                })
                .build();
        return chain.filter(exchange.mutate().request(cleaned).build());
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    private String getAuthHeader(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header == null || header.trim().isEmpty()) {
            return null;
        }
        String trimmed = header.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = trimmed.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
