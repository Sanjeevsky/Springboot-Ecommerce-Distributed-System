package com.sanjeevsky.reviewservice.controller;

import com.sanjeevsky.platform.response.ApiResponse;
import com.sanjeevsky.reviewservice.dto.ReviewSummary;
import com.sanjeevsky.reviewservice.model.Review;
import com.sanjeevsky.reviewservice.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/review-service")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/review")
    public ResponseEntity<ApiResponse<Review>> addReview(
            @RequestHeader("X-User") String userId,
            @RequestBody Review review) {
        log.info("Received request to add review from userId: {}", userId);
        return new ResponseEntity<>(ApiResponse.ok("Review submitted for moderation", reviewService.addReview(userId, review)), HttpStatus.CREATED);
    }

    @GetMapping("/review/product/{productId}")
    public ResponseEntity<ApiResponse<List<Review>>> getApprovedReviews(
            @PathVariable("productId") UUID productId) {
        log.info("Received request to fetch approved reviews for productId: {}", productId);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getApprovedReviews(productId)));
    }

    @GetMapping("/review/product/{productId}/summary")
    public ResponseEntity<ApiResponse<ReviewSummary>> getProductSummary(
            @PathVariable("productId") UUID productId) {
        log.info("Received request to fetch review summary for productId: {}", productId);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getProductSummary(productId)));
    }

    @PutMapping("/review/{id}/moderate")
    public ResponseEntity<ApiResponse<Review>> moderateReview(
            @PathVariable("id") UUID id,
            @RequestParam("status") String status) {
        log.info("Received request to moderate review id: {} to status: {}", id, status);
        return ResponseEntity.ok(ApiResponse.ok("Review moderated successfully", reviewService.moderateReview(id, status)));
    }

    @GetMapping("/review/my")
    public ResponseEntity<ApiResponse<List<Review>>> getUserReviews(
            @RequestHeader("X-User") String userId) {
        log.info("Received request to fetch reviews for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getUserReviews(userId)));
    }
}
