package com.sanjeevsky.customerservice.model.feign;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Setter
public class Product {
    private UUID id;
    private String name;
    private String description;
    private Brand brand;
    private int status;  //  0->inactive 1 -> active
    private Category category;
    private SubCategory subCategory;
    private String model;
    private double mrpPrice;
    private double salePrice;
    private double gstValue;
    private double discount;
    private boolean hasVariant;
    private ArrayList<String> images;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private List<Variant> variants = new ArrayList<>();

}
