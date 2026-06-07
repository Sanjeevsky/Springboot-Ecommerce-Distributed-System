package com.sanjeevsky.orderservice.repository;

import com.sanjeevsky.orderservice.model.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
    Optional<SagaInstance> findByOrderId(UUID orderId);
}
