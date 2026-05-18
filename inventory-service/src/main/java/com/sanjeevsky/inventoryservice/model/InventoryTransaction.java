package com.sanjeevsky.inventoryservice.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID id;

    @Type(type = "org.hibernate.type.UUIDCharType")
    @Column(nullable = false)
    private UUID inventoryId;

    @Type(type = "org.hibernate.type.UUIDCharType")
    @Column(nullable = true)
    private UUID orderId;

    // RESTOCK, RESERVE, RELEASE
    private String type;

    private int quantity;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
