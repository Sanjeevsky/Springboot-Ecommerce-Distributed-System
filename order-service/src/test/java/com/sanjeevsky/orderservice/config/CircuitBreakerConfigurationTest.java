package com.sanjeevsky.orderservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the tuning that makes the Feign circuit breakers actually shed load. A regression
 * here (e.g. dropping the time limiter back to the 15s read-timeout, or widening the window
 * so the breaker never opens) would silently restore the original "hang on every call"
 * behaviour, so these values are pinned.
 */
class CircuitBreakerConfigurationTest {

    @Test
    void timeLimiterFailsFastWellBelowTheFeignReadTimeout() {
        TimeLimiterConfig config = CircuitBreakerConfiguration.timeLimiterConfig();

        assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(4));
        assertThat(config.getTimeoutDuration()).isLessThan(Duration.ofSeconds(15));
        assertThat(config.shouldCancelRunningFuture()).isTrue();
    }

    @Test
    void circuitBreakerOpensOnSustainedFailure() {
        CircuitBreakerConfig config = CircuitBreakerConfiguration.circuitBreakerConfig();

        assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(Duration.ofSeconds(10).toMillis());
    }

    @Test
    void exposesACustomizerForTheFeignCircuitBreakerFactory() {
        Customizer<Resilience4JCircuitBreakerFactory> customizer =
                new CircuitBreakerConfiguration().feignCircuitBreakerCustomizer();

        assertThat(customizer).isNotNull();
    }
}
