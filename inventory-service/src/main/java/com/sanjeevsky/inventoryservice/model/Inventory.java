package com.sanjeevsky.inventoryservice.model;

import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"productId", "variantId"}),
        indexes = @javax.persistence.Index(name = "idx_inventory_product_id", columnList = "productId")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID id;

    @Type(type = "org.hibernate.type.UUIDCharType")
    @Column(nullable = false)
    private UUID productId;

    @Type(type = "org.hibernate.type.UUIDCharType")
    @Column(nullable = true)
    private UUID variantId;

    @Column(nullable = false)
    @Builder.Default
    private int totalQty = 0;

    @Column(nullable = false)
    @Builder.Default
    private int reservedQty = 0;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public int getAvailableQty() {
        return totalQty - reservedQty;
    }
}
