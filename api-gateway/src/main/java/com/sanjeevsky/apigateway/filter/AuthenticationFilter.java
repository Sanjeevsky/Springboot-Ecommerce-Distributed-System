package com.sanjeevsky.apigateway.filter;

import io.jsonwebtoken.Claims;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private RouterValidator routerValidator;//custom route validator
    @Autowired
    private JwtUtil jwtUtil;

    @SneakyThrows
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (routerValidator.isSecured.test(request)) {
            if (this.isAuthMissing(request))
                return this.onError(exchange, "Authorization header is missing in request", HttpStatus.UNAUTHORIZED);

            final String token = this.getAuthHeader(request);

            if (jwtUtil.isInvalid(token))
                return this.onError(exchange, "Authorization header is invalid", HttpStatus.UNAUTHORIZED);
            String errMsg = this.populateRequestWithHeaders(exchange, token);
            if (!errMsg.isEmpty()){
               return this.onError(exchange,errMsg,HttpStatus.BAD_REQUEST);
            }
        }
        return chain.filter(exchange);
    }


    /*PRIVATE*/

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    private String getAuthHeader(ServerHttpRequest request) {
        return request.getHeaders().getOrEmpty("Authorization").get(0);
    }

    private boolean isAuthMissing(ServerHttpRequest request) {
        return !request.getHeaders().containsKey("Authorization");
    }

    private String populateRequestWithHeaders(ServerWebExchange exchange, String token)  {
        Claims claims = null;
        try {
            claims = jwtUtil.getAllClaimsFromToken(token);
            exchange.getRequest().mutate()
                    .header("user", claims.getSubject())
                    .build();
        } catch (Exception e) {
           return e.getMessage();

        }

        return "";
    }
}