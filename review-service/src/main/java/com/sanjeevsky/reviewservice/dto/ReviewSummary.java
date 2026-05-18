package com.sanjeevsky.reviewservice.dto;

import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSummary {

    private UUID productId;
    private double averageRating;
    private int totalReviews;
    private Map<Integer, Long> ratingDistribution;
}
