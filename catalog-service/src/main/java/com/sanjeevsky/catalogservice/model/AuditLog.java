package com.sanjeevsky.catalogservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only record of an admin mutation to the sales catalog (products,
 * prices, status). Written by {@code AuditService} and read by the Studio
 * activity view; never updated or deleted.
 */
@Entity
@Table(name = "catalog_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Type(type = "org.hibernate.type.UUIDCharType")
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Kind of entity changed, e.g. PRODUCT. */
    @Column(nullable = false)
    private String entityType;

    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID entityId;

    /** CREATE, UPDATE, RETIRE. */
    @Column(nullable = false)
    private String action;

    /** Email of the admin who made the change, or "system" when unattributed. */
    @Column(nullable = false)
    private String actor;

    @Column(length = 1024)
    private String summary;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
