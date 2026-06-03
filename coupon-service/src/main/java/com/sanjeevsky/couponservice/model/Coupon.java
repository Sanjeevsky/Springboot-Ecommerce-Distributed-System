package com.sanjeevsky.couponservice.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
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
    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "type is required")
    private String type; // "PERCENTAGE" or "FIXED"

    @Positive(message = "value must be greater than zero")
    private double value;

    @Builder.Default
    @PositiveOrZero(message = "minOrderAmount must not be negative")
    private double minOrderAmount = 0;

    @Builder.Default
    @Min(value = -1, message = "maxUsageCount must be -1 or greater")
    private int maxUsageCount = -1;

    @Builder.Default
    @PositiveOrZero(message = "usedCount must not be negative")
    private int usedCount = 0;

    private LocalDate expiryDate;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
