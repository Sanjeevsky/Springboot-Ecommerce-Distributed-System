package com.sanjeevsky.inventoryservice.model;

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
 * Append-only record of an admin stock change. Mirrors the catalog audit shape
 * so the Studio activity view can render both uniformly. The existing
 * {@code InventoryTransaction} ledger stays the operational record (incl. saga
 * reserve/release); this captures who changed stock and by how much.
 */
@Entity
@Table(name = "inventory_audit_log")
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

    /** Kind of entity changed, e.g. STOCK. */
    @Column(nullable = false)
    private String entityType;

    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID entityId;

    /** RESTOCK, STOCK_SET. */
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
