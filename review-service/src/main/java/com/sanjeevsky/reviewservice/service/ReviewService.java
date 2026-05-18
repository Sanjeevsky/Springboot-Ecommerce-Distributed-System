package com.sanjeevsky.reviewservice.service;

import com.sanjeevsky.reviewservice.dto.ReviewSummary;
import com.sanjeevsky.reviewservice.model.Review;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    Review addReview(String userId, Review review);

    List<Review> getApprovedReviews(UUID productId);

    ReviewSummary getProductSummary(UUID productId);

    Review moderateReview(UUID id, String status);

    List<Review> getUserReviews(String userId);
}
