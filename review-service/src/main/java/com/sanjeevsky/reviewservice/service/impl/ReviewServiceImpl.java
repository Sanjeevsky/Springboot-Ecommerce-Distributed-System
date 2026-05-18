package com.sanjeevsky.reviewservice.service.impl;

import com.sanjeevsky.reviewservice.dto.ReviewSummary;
import com.sanjeevsky.reviewservice.exceptions.ReviewNotFoundException;
import com.sanjeevsky.reviewservice.model.Review;
import com.sanjeevsky.reviewservice.repository.ReviewRepository;
import com.sanjeevsky.reviewservice.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Override
    public Review addReview(String userId, Review review) {
        log.info("Adding review for productId: {} by userId: {}", review.getProductId(), userId);
        review.setUserId(userId);
        review.setStatus("PENDING");
        Review saved = reviewRepository.save(review);
        log.info("Review saved with id: {}", saved.getId());
        return saved;
    }

    @Override
    public List<Review> getApprovedReviews(UUID productId) {
        log.info("Fetching approved reviews for productId: {}", productId);
        return reviewRepository.findByProductIdAndStatus(productId, "APPROVED");
    }

    @Override
    public ReviewSummary getProductSummary(UUID productId) {
        log.info("Fetching review summary for productId: {}", productId);
        List<Review> approvedReviews = reviewRepository.findByProductIdAndStatus(productId, "APPROVED");

        if (approvedReviews.isEmpty()) {
            return ReviewSummary.builder()
                    .productId(productId)
                    .averageRating(0)
                    .totalReviews(0)
                    .ratingDistribution(Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L))
                    .build();
        }

        double averageRating = approvedReviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        Map<Integer, Long> ratingDistribution = approvedReviews.stream()
                .collect(Collectors.groupingBy(Review::getRating, Collectors.counting()));

        for (int i = 1; i <= 5; i++) {
            ratingDistribution.putIfAbsent(i, 0L);
        }

        return ReviewSummary.builder()
                .productId(productId)
                .averageRating(Math.round(averageRating * 10.0) / 10.0)
                .totalReviews(approvedReviews.size())
                .ratingDistribution(ratingDistribution)
                .build();
    }

    @Override
    public Review moderateReview(UUID id, String status) {
        log.info("Moderating review id: {} to status: {}", id, status);
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ReviewNotFoundException("Review not found with id: " + id));
        review.setStatus(status);
        Review updated = reviewRepository.save(review);
        log.info("Review {} moderated to status: {}", id, status);
        return updated;
    }

    @Override
    public List<Review> getUserReviews(String userId) {
        log.info("Fetching reviews for userId: {}", userId);
        return reviewRepository.findByUserId(userId);
    }
}
