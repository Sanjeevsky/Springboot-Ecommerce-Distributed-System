package com.sanjeevsky.apigateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Runs before the JWT AuthenticationFilter. Ensures every downstream request carries
 * an X-Correlation-ID header. If the client sent one, it is forwarded unchanged;
 * otherwise a new UUID is generated. The same value is echoed back in the response
 * header so callers can correlate their logs with service logs.
 */
@Component
public class CorrelationIdGatewayFilter implements WebFilter, Ordered {

    static final String HEADER = "X-Correlation-ID";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String cid = correlationId;
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER, cid)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        mutatedExchange.getResponse().getHeaders().add(HEADER, cid);

        return chain.filter(mutatedExchange);
    }
}
