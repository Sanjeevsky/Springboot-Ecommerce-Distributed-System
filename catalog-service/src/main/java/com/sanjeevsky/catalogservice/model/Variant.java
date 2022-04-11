package com.sanjeevsky.catalogservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Variant {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID id;
    private String condition1Name;
    private String condition1Value;
    private String condition2Name;
    private String condition2Value;
    @Column(nullable = false)
    private double mrpPrice;
    @Column(nullable = false)
    private double salePrice;
    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Product product;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime modifiedAt;

    public void setProduct(Product product) {
        this.product = product;
    }
}
