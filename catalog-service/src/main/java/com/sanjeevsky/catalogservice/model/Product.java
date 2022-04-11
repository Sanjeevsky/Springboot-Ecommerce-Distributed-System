package com.sanjeevsky.catalogservice.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID id;
    private String name;
    private String description;
    @ManyToOne
    @JoinColumn(name = "brand_id")
    private Brand brand;
    private int status;  //  0->inactive 1 -> active
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;
    @ManyToOne()
    @JoinColumn(name = "sub_category_id")
    private SubCategory subCategory;
    private String model;
    private double mrpPrice;
    private double salePrice;
    private double gstValue;
    private double discount;
    private boolean hasVariant;
    private ArrayList<String> images;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime modifiedAt;

    @OneToMany(mappedBy = "product", orphanRemoval = true)
    private List<Variant> variants = new ArrayList<>();

}
