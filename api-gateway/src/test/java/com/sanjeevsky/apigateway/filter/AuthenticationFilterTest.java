package com.sanjeevsky.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {

    private final RouterValidator routerValidator = new RouterValidator();
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final AuthenticationFilter filter = new AuthenticationFilter(routerValidator, jwtUtil);

    @Test
    void filter_openRoute_passesThroughWithoutAuth() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth-service/login").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, capturedChain(chainCalled, new AtomicReference<>())).block();

        assertTrue(chainCalled.get());
        assertNull(exchange.getResponse().getStatusCode());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_securedRouteMissingAuth_returnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/orders").build());

        filter.filter(exchange, capturedChain(new AtomicBoolean(false), new AtomicReference<>())).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_securedRouteInvalidAuth_returnsUnauthorized() throws Exception {
        when(jwtUtil.getAllClaimsFromToken("bad-token")).thenThrow(new Exception("Invalid JWT token"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/orders")
                        .header("Authorization", "Bearer bad-token")
                        .build());

        filter.filter(exchange, capturedChain(new AtomicBoolean(false), new AtomicReference<>())).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_securedRouteRawTokenAuth_returnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/orders")
                        .header("Authorization", "good-token")
                        .build());

        filter.filter(exchange, capturedChain(new AtomicBoolean(false), new AtomicReference<>())).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_securedRouteEmptyBearerAuth_returnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/orders")
                        .header("Authorization", "Bearer ")
                        .build());

        filter.filter(exchange, capturedChain(new AtomicBoolean(false), new AtomicReference<>())).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_securedRouteValidBearerAuth_addsUserHeader() throws Exception {
        Claims claims = Jwts.claims().setSubject("buyer@example.com");
        when(jwtUtil.getAllClaimsFromToken("good-token")).thenReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/orders")
                        .header("Authorization", "Bearer good-token")
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        filter.filter(exchange, capturedChain(chainCalled, capturedExchange)).block();

        assertTrue(chainCalled.get());
        assertEquals("buyer@example.com", capturedExchange.get().getRequest().getHeaders().getFirst("X-User"));
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_securedRouteTokenWithoutRoleClaim_defaultsRoleHeaderToCustomer() throws Exception {
        Claims claims = Jwts.claims().setSubject("buyer@example.com");
        when(jwtUtil.getAllClaimsFromToken("good-token")).thenReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/orders")
                        .header("Authorization", "Bearer good-token")
                        .build());
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        filter.filter(exchange, capturedChain(new AtomicBoolean(false), capturedExchange)).block();

        assertEquals("CUSTOMER", capturedExchange.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void filter_securedRouteAdminToken_addsAdminRoleHeader() throws Exception {
        Claims claims = Jwts.claims().setSubject("admin@trove.local");
        claims.put("role", "ADMIN");
        when(jwtUtil.getAllClaimsFromToken("admin-token")).thenReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/orders")
                        .header("Authorization", "Bearer admin-token")
                        .header("X-User-Role", "spoofed")
                        .build());
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        filter.filter(exchange, capturedChain(new AtomicBoolean(false), capturedExchange)).block();

        assertEquals(
                List.of("ADMIN"),
                capturedExchange.get().getRequest().getHeaders().get("X-User-Role"));
    }

    @Test
    void filter_openRoute_stripsClientIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/catalog-service/product/list")
                        .header("X-User", "spoofed@example.com")
                        .header("X-User-Role", "ADMIN")
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        filter.filter(exchange, capturedChain(chainCalled, capturedExchange)).block();

        assertTrue(chainCalled.get());
        assertNull(capturedExchange.get().getRequest().getHeaders().getFirst("X-User"));
        assertNull(capturedExchange.get().getRequest().getHeaders().getFirst("X-User-Role"));
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_securedRouteValidBearerAuth_replacesClientUserHeader() throws Exception {
        Claims claims = Jwts.claims().setSubject("buyer@example.com");
        when(jwtUtil.getAllClaimsFromToken("good-token")).thenReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/orders")
                        .header("Authorization", "Bearer good-token")
                        .header("X-User", "spoofed@example.com")
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        filter.filter(exchange, capturedChain(chainCalled, capturedExchange)).block();

        assertTrue(chainCalled.get());
        assertEquals(
                List.of("buyer@example.com"),
                capturedExchange.get().getRequest().getHeaders().get("X-User"));
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_securedRouteLowercaseBearerAuth_addsUserHeader() throws Exception {
        Claims claims = Jwts.claims().setSubject("buyer@example.com");
        when(jwtUtil.getAllClaimsFromToken("good-token")).thenReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/orders")
                        .header("Authorization", "bearer good-token")
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        filter.filter(exchange, capturedChain(chainCalled, capturedExchange)).block();

        assertTrue(chainCalled.get());
        assertEquals("buyer@example.com", capturedExchange.get().getRequest().getHeaders().getFirst("X-User"));
        assertNull(exchange.getResponse().getStatusCode());
    }

    private GatewayFilterChain capturedChain(
            AtomicBoolean chainCalled,
            AtomicReference<ServerWebExchange> capturedExchange) {
        return exchange -> {
            chainCalled.set(true);
            capturedExchange.set(exchange);
            return Mono.empty();
        };
    }
}
