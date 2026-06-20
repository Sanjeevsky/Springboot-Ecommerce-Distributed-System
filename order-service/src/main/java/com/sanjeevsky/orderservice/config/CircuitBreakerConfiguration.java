package com.sanjeevsky.orderservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Tunes the circuit breakers that Spring Cloud OpenFeign actually uses.
 *
 * <p>With {@code feign.circuitbreaker.enabled=true} every Feign call is wrapped by the
 * {@link Resilience4JCircuitBreakerFactory}. That factory is configured in Java — it does
 * <strong>not</strong> read the {@code resilience4j.circuitbreaker.instances.*} properties
 * (those are consumed by the separate resilience4j-spring-boot2 registry, which the Feign
 * path never touches). Without this customizer the Feign breakers run on library defaults
 * (a 100-call sliding window with no time limit), so a slow downstream — e.g. a hung
 * payment-service — blocks every order worker thread on the Feign read timeout instead of
 * tripping the breaker. This bean makes the breaker genuinely shed load:
 *
 * <ul>
 *   <li><b>4s time limit</b> — a hung dependency fails the call in 4s instead of riding the
 *       15s Feign read timeout (the 15s read timeout / time-limiter properties remain as a
 *       cold-start backstop and to satisfy config validation).</li>
 *   <li><b>opens after sustained failure</b> — a 10-call window, 5-call minimum, 50% failure
 *       threshold; once open, calls fail fast into the Feign fallback for 10s before a
 *       half-open trial of 3 calls.</li>
 * </ul>
 *
 * <p>{@code configureDefault} applies to every Feign client (cart, payment, coupon, customer,
 * inventory), independent of the per-method circuit id naming.
 */
@Configuration
public class CircuitBreakerConfiguration {

    static final Duration TIME_LIMIT = Duration.ofSeconds(4);
    static final int SLIDING_WINDOW_SIZE = 10;
    static final int MINIMUM_CALLS = 5;
    static final float FAILURE_RATE_THRESHOLD = 50f;
    static final Duration WAIT_IN_OPEN_STATE = Duration.ofSeconds(10);

    static CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(SLIDING_WINDOW_SIZE)
                .minimumNumberOfCalls(MINIMUM_CALLS)
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(WAIT_IN_OPEN_STATE)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
    }

    static TimeLimiterConfig timeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(TIME_LIMIT)
                .cancelRunningFuture(true)
                .build();
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> feignCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(circuitBreakerConfig())
                .timeLimiterConfig(timeLimiterConfig())
                .build());
    }
}
