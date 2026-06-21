package com.sanjeevsky.orderservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeignCircuitBreakerStateMetricsTest {

    private double stateValue(SimpleMeterRegistry meter, String name, String state) {
        return meter.get("resilience4j.circuitbreaker.state")
                .tags("name", name, "state", state).gauge().value();
    }

    @Test
    void exportsStateGaugesForRegistryBreakers_includingOnesCreatedAfterStartup() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker("HardCodedTarget#initiatePayment(PaymentRequest)"); // present at startup
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        FeignCircuitBreakerStateMetrics exporter = new FeignCircuitBreakerStateMetrics(registry, meter);

        exporter.reconcile();
        assertThat(meter.find("resilience4j.circuitbreaker.state")
                .tag("name", "HardCodedTarget#initiatePayment(PaymentRequest)").gauges())
                .as("a Feign breaker present at reconcile time is exported (one series per state)")
                .hasSize(CircuitBreaker.State.values().length);

        // Feign breakers are created lazily on first call — a new one must be picked up on the
        // next reconcile, which is the whole point of polling rather than relying on entry events.
        registry.circuitBreaker("HardCodedTarget#clearCart(String)");
        exporter.reconcile();
        assertThat(meter.find("resilience4j.circuitbreaker.state")
                .tag("name", "HardCodedTarget#clearCart(String)").gauges())
                .as("a breaker created after startup is exported on the next reconcile")
                .isNotEmpty();
    }

    @Test
    void stateGaugeTracksTheBreakerTransitioningOpen() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker cb = registry.circuitBreaker("HardCodedTarget#getAddress(String,UUID)");
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        new FeignCircuitBreakerStateMetrics(registry, meter).reconcile();

        assertThat(stateValue(meter, cb.getName(), "closed")).isEqualTo(1.0);
        assertThat(stateValue(meter, cb.getName(), "open")).isEqualTo(0.0);

        cb.transitionToOpenState();

        assertThat(stateValue(meter, cb.getName(), "open")).isEqualTo(1.0);
        assertThat(stateValue(meter, cb.getName(), "closed")).isEqualTo(0.0);
    }
}
