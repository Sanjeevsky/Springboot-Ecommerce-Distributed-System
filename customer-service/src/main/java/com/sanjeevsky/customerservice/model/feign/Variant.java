package com.sanjeevsky.customerservice.model.feign;

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

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Variant {
    private UUID id;
    private String condition1Name;
    private String condition1Value;
    private String condition2Name;
    private String condition2Value;
    private double mrpPrice;
    private double salePrice;
    private Product product;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    public void setProduct(Product product) {
        this.product = product;
    }
}
