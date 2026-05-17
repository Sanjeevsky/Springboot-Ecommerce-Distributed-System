package com.sanjeevsky.shoppingcartservice.model;

import lombok.*;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cart_item")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID cartId;

    @Column(name = "product_id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID productId;

    @Column(name = "variant_id")
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID variantId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false)
    private double unitPrice;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "added_at")
    private LocalDateTime addedAt;
}
