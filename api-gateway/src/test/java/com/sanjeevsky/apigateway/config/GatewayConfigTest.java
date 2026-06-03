package com.sanjeevsky.apigateway.config;

import com.sanjeevsky.apigateway.filter.AuthenticationFilter;
import com.sanjeevsky.apigateway.filter.JwtUtil;
import com.sanjeevsky.apigateway.filter.RouterValidator;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GatewayConfigTest {

    @Test
    void routesExposeStandardCartAliasWithoutRawShoppingCartRoute() {
        StaticApplicationContext context = routeBuilderContext();
        try {
            GatewayConfig gatewayConfig = new GatewayConfig(authenticationFilter());

            RouteLocator routeLocator = gatewayConfig.routes(new RouteLocatorBuilder(context));
            Map<String, Route> routes = routeLocator.getRoutes().collectMap(Route::getId).block();

            assertNotNull(routes);
            assertTrue(routes.containsKey("cart-service"));
            assertFalse(routes.containsKey("shopping-cart-service"));
            assertEquals("lb://shopping-cart-service", routes.get("cart-service").getUri().toString());
            assertTrue(matches(routes.get("cart-service"), "/cart-service/cart"));
            assertFalse(matches(routes.get("cart-service"), "/shopping-cart-service/cart"));
        } finally {
            context.close();
        }
    }

    @Test
    void routesCoverAllGatewayServicePrefixes() {
        StaticApplicationContext context = routeBuilderContext();
        try {
            GatewayConfig gatewayConfig = new GatewayConfig(authenticationFilter());

            RouteLocator routeLocator = gatewayConfig.routes(new RouteLocatorBuilder(context));
            Map<String, Route> routes = routeLocator.getRoutes().collectMap(Route::getId).block();

            assertNotNull(routes);
            assertEquals(11, routes.size());
            assertRoute(routes, "auth-service", "lb://auth-service", "/auth-service/login");
            assertRoute(routes, "catalog-service", "lb://catalog-service", "/catalog-service/product/list");
            assertRoute(routes, "customer-service", "lb://customer-service", "/customer-service/address");
            assertRoute(routes, "payment-service", "lb://payment-service", "/payment-service/initiate");
            assertRoute(routes, "inventory-service", "lb://inventory-service", "/inventory-service/stock");
            assertRoute(routes, "notification-service", "lb://notification-service", "/notification-service/notifications");
            assertRoute(routes, "order-service", "lb://order-service", "/order-service/order");
            assertRoute(routes, "coupon-service", "lb://coupon-service", "/coupon-service/coupon");
            assertRoute(routes, "review-service", "lb://review-service", "/review-service/review");
            assertRoute(routes, "wishlist-service", "lb://wishlist-service", "/wishlist-service/wishlist");
        } finally {
            context.close();
        }
    }

    private void assertRoute(Map<String, Route> routes, String id, String uri, String samplePath) {
        Route route = routes.get(id);
        assertNotNull(route, id + " route should exist");
        assertEquals(uri, route.getUri().toString());
        assertTrue(matches(route, samplePath), id + " should match " + samplePath);
    }

    private AuthenticationFilter authenticationFilter() {
        return new AuthenticationFilter(new RouterValidator(), mock(JwtUtil.class));
    }

    private StaticApplicationContext routeBuilderContext() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("pathRoutePredicateFactory", PathRoutePredicateFactory.class);
        context.refresh();
        return context;
    }

    private boolean matches(Route route, String path) {
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(
                MockServerWebExchange.from(MockServerHttpRequest.get(path).build()))).block());
    }
}
