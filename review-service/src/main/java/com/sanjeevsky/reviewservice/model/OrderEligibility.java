package com.sanjeevsky.reviewservice.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "order_eligibility",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "productId"}),
        indexes = @Index(name = "idx_eligibility_user_id", columnList = "userId")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID productId;

    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID orderId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
