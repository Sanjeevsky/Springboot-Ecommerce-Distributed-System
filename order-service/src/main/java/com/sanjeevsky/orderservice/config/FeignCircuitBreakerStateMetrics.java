package com.sanjeevsky.orderservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exports the per-Feign-client circuit-breaker <em>state</em> to Prometheus as
 * {@code resilience4j_circuitbreaker_state{name="HardCodedTarget#…",state="open"}}.
 *
 * <p><b>Why this exists.</b> The Spring Cloud OpenFeign breakers are created <em>lazily</em>
 * (on the first call to each Feign method). Although both resilience4j-spring-boot2 and Spring
 * Cloud's own {@code MicrometerResilience4JCustomizerConfiguration} bind
 * {@code TaggedCircuitBreakerMetrics} to the registry, in this version stack
 * (spring-cloud-circuitbreaker 2.0.2 / resilience4j 1.7.0) the registry's {@code onEntryAdded}
 * event does not reliably meter these lazily-created breakers — so only the eager
 * {@code resilience4j.circuitbreaker.instances.*} instances reach Prometheus, while the Feign
 * breakers' state stays visible only via {@code /actuator/circuitbreakerevents}. (This is why an
 * earlier "just bind the registry" attempt was reverted — it is redundant with Spring Cloud's and
 * does not work; verified empirically.)
 *
 * <p><b>How.</b> Rather than rely on the entry-added event, this reconciler polls the
 * {@link CircuitBreakerRegistry} — the same registry the {@code /actuator/circuitbreakers}
 * endpoint reads, which provably contains the Feign breakers — and registers a Micrometer state
 * gauge for each breaker it has not seen yet. Registration uses the standard
 * {@code resilience4j.circuitbreaker.state} name/tags, so the metric is uniform across Feign and
 * property-defined breakers; re-registering an already-metered instance is a no-op (Micrometer
 * meter builders are idempotent by id). A breaker's gauges appear within one reconcile interval of
 * its first call and then track its state live.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignCircuitBreakerStateMetrics {

    private static final String STATE_METRIC = "resilience4j.circuitbreaker.state";

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    /** Meter whatever exists at startup (the eager property instances) immediately. */
    @PostConstruct
    void init() {
        reconcile();
    }

    @Scheduled(fixedDelayString = "${circuitbreaker.metrics.reconcile-ms:15000}")
    public void reconcile() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::ensureStateGauges);
    }

    private void ensureStateGauges(CircuitBreaker cb) {
        if (!registered.add(cb.getName())) {
            return; // already metered
        }
        for (CircuitBreaker.State state : CircuitBreaker.State.values()) {
            Gauge.builder(STATE_METRIC, cb, c -> c.getState() == state ? 1.0 : 0.0)
                    .tag("name", cb.getName())
                    .tag("state", state.name().toLowerCase())
                    .strongReference(true)
                    .register(meterRegistry);
        }
        log.debug("Registered circuit-breaker state gauges for '{}'", cb.getName());
    }
}
