package com.sanjeevsky.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistent log of one order saga instance. This is the orchestrator's source of truth:
 * each step transition is recorded here, which is what makes the distributed flow observable
 * and the event handlers idempotent (they guard on {@link #status}).
 */
@Entity
@Table(name = "order_saga")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    /** Human-readable label of the last step taken, for tracing/learning. */
    @Column(name = "current_step")
    private String currentStep;

    /** Whether to simulate a payment failure (learning lever to exercise compensation). */
    private boolean simulatePaymentFailure;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
