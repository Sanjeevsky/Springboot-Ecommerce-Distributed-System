package com.sanjeevsky.couponservice.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coupon")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID id;

    @Column(unique = true, nullable = false)
    private String code;

    private String type; // "PERCENTAGE" or "FIXED"

    private double value;

    @Builder.Default
    private double minOrderAmount = 0;

    @Builder.Default
    private int maxUsageCount = -1;

    @Builder.Default
    private int usedCount = 0;

    private LocalDate expiryDate;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
