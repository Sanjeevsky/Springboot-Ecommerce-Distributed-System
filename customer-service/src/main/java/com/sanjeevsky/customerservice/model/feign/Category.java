package com.sanjeevsky.customerservice.model.feign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class Category {
    private UUID id;
    private String categoryName;
    private List<SubCategory> subCategories;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
