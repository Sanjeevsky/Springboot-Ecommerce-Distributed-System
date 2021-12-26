package com.sanjeevsky.catalogservice.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    private Long id;
    private String name;
    private String description;
    @ManyToOne
    @JoinColumn(name = "brand_id")
    private Brand brand;
    private int status;  //  0->inactive 1 -> active
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;
    @ManyToOne
    @JoinColumn(name = "sub_category_id")
    private SubCategory subCategory;
    private String model;
    private double mrpPrice;
    private double salePrice;
    @OneToMany(cascade = CascadeType.ALL)
    private List<Variant> variants;
    @ManyToMany(cascade = CascadeType.ALL)
    private List<Discount> discounts;
    @CreatedDate
    private Date createdAt;
    @LastModifiedDate
    private Date modifiedAt;
    private boolean hasVariant;
    private ArrayList<String> images;
}
