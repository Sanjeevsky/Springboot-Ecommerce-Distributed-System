package com.sanjeevsky.orderservice.service;

import com.sanjeevsky.orderservice.model.SagaInstance;
import com.sanjeevsky.orderservice.model.SagaStatus;
import com.sanjeevsky.orderservice.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically compensates sagas that have parked in an in-flight state past their timeout.
 *
 * <p>{@link OrderSagaOrchestrator} only compensates on a downstream <em>reply</em> — a
 * {@code PaymentFailed} event. If a participant is merely <em>unreachable</em> (payment-service
 * down, its command sitting unconsumed on {@code payment-commands}), the saga stays
 * {@code STOCK_RESERVED} forever and the reserved stock leaks. This reaper closes that gap: it
 * finds sagas stuck in {@link #IN_FLIGHT} longer than {@code saga.timeout} and drives each to
 * {@code COMPENSATED} via {@link OrderSagaOrchestrator#compensateStuckSaga}, releasing the stock.
 *
 * <p>Gated by {@code saga.reaper.enabled} (default on) so it can be disabled in tests/diagnostics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "saga.reaper.enabled", matchIfMissing = true)
public class SagaTimeoutReaper {

    /** Durable states that await a downstream reply; PAYMENT_CONFIRMED/COMPENSATING are transient. */
    private static final List<SagaStatus> IN_FLIGHT = List.of(SagaStatus.STARTED, SagaStatus.STOCK_RESERVED);

    private final SagaInstanceRepository sagaRepository;
    private final OrderSagaOrchestrator orchestrator;

    /** How long a saga may sit in an in-flight state before the reaper compensates it. */
    @Value("${saga.timeout:PT2M}")
    private Duration timeout;

    @Scheduled(
            fixedDelayString = "${saga.reaper.interval-ms:30000}",
            initialDelayString = "${saga.reaper.initial-delay-ms:60000}")
    public void reapTimedOutSagas() {
        LocalDateTime cutoff = LocalDateTime.now().minus(timeout);
        List<SagaInstance> stuck = sagaRepository.findByStatusInAndUpdatedAtBefore(IN_FLIGHT, cutoff);
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("Saga reaper found {} saga(s) stuck in-flight >{}; compensating", stuck.size(), timeout);
        for (SagaInstance saga : stuck) {
            String reason = "Saga timed out in " + saga.getStatus() + " after " + timeout
                    + " with no downstream reply (participant unreachable?)";
            try {
                // Each runs in its own transaction, so one failure doesn't abort the rest of the batch.
                orchestrator.compensateStuckSaga(saga.getOrderId(), reason);
            } catch (RuntimeException ex) {
                log.error("Failed to compensate stuck saga orderId={}: {}", saga.getOrderId(), ex.getMessage(), ex);
            }
        }
    }
}
