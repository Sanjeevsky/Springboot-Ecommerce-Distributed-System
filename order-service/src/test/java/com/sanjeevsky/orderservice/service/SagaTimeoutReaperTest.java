package com.sanjeevsky.orderservice.service;

import com.sanjeevsky.orderservice.model.SagaInstance;
import com.sanjeevsky.orderservice.model.SagaStatus;
import com.sanjeevsky.orderservice.repository.SagaInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaTimeoutReaperTest {

    @Mock SagaInstanceRepository sagaRepository;
    @Mock OrderSagaOrchestrator orchestrator;

    @InjectMocks SagaTimeoutReaper reaper;

    private SagaInstance stuck(SagaStatus status) {
        return SagaInstance.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .userId("buyer@example.com")
                .status(status)
                .build();
    }

    @Test
    void reap_compensatesEveryStuckSaga() {
        ReflectionTestUtils.setField(reaper, "timeout", Duration.ofMinutes(2));
        SagaInstance a = stuck(SagaStatus.STOCK_RESERVED);
        SagaInstance b = stuck(SagaStatus.STARTED);
        when(sagaRepository.findByStatusInAndUpdatedAtBefore(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(a, b));

        reaper.reapTimedOutSagas();

        verify(orchestrator).compensateStuckSaga(eq(a.getOrderId()), anyString());
        verify(orchestrator).compensateStuckSaga(eq(b.getOrderId()), anyString());
    }

    @Test
    void reap_doesNothingWhenNoSagasAreStuck() {
        ReflectionTestUtils.setField(reaper, "timeout", Duration.ofMinutes(2));
        when(sagaRepository.findByStatusInAndUpdatedAtBefore(any(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        reaper.reapTimedOutSagas();

        verify(orchestrator, never()).compensateStuckSaga(any(), anyString());
    }

    @Test
    void reap_continuesAfterOneSagaFails() {
        ReflectionTestUtils.setField(reaper, "timeout", Duration.ofMinutes(2));
        SagaInstance failing = stuck(SagaStatus.STOCK_RESERVED);
        SagaInstance ok = stuck(SagaStatus.STOCK_RESERVED);
        when(sagaRepository.findByStatusInAndUpdatedAtBefore(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(failing, ok));
        doThrow(new RuntimeException("boom"))
                .when(orchestrator).compensateStuckSaga(eq(failing.getOrderId()), anyString());

        reaper.reapTimedOutSagas();

        // the failure of the first saga must not prevent the second from being compensated
        verify(orchestrator).compensateStuckSaga(eq(ok.getOrderId()), anyString());
    }
}
