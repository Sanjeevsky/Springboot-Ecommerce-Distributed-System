package com.sanjeevsky.orderservice.repository;

import com.sanjeevsky.orderservice.model.SagaInstance;
import com.sanjeevsky.orderservice.model.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
    Optional<SagaInstance> findByOrderId(UUID orderId);

    /**
     * Sagas parked in an in-flight {@code status} whose last transition ({@code updatedAt}) is
     * older than {@code cutoff} — i.e. stuck waiting on a downstream reply that never came
     * (e.g. {@code STOCK_RESERVED} when payment-service is unreachable). Drives the timeout reaper.
     */
    List<SagaInstance> findByStatusInAndUpdatedAtBefore(Collection<SagaStatus> status, LocalDateTime cutoff);
}
