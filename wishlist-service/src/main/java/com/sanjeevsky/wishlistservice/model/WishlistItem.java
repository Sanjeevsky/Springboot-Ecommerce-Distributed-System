package com.sanjeevsky.wishlistservice.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "wishlist_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "productId"}),
        indexes = @Index(name = "idx_wishlist_user_id", columnList = "userId")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItem {

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

    private String productName;

    private double salePrice;

    @CreationTimestamp
    private LocalDateTime addedAt;
}
