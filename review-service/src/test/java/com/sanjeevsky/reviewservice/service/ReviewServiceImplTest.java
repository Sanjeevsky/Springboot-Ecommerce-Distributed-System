package com.sanjeevsky.reviewservice.service;

import com.sanjeevsky.reviewservice.dto.ReviewSummary;
import com.sanjeevsky.reviewservice.exceptions.InvalidReviewRequestException;
import com.sanjeevsky.reviewservice.exceptions.ReviewNotFoundException;
import com.sanjeevsky.reviewservice.exceptions.UnauthorizedReviewException;
import com.sanjeevsky.reviewservice.model.Review;
import com.sanjeevsky.reviewservice.repository.OrderEligibilityRepository;
import com.sanjeevsky.reviewservice.repository.ReviewRepository;
import com.sanjeevsky.reviewservice.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderEligibilityRepository eligibilityRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final String USER_ID = "user@example.com";

    private Review pendingReview(int rating) {
        return Review.builder()
                .id(UUID.randomUUID())
                .productId(PRODUCT_ID)
                .userId(USER_ID)
                .rating(rating)
                .title("Great product")
                .comment("Really enjoyed it")
                .status("PENDING")
                .build();
    }

    private Review approvedReview(int rating) {
        Review r = pendingReview(rating);
        r.setStatus("APPROVED");
        return r;
    }

    // ─── addReview ────────────────────────────────────────────────────────────

    @Test
    void addReview_eligible_setsUserIdAndPendingStatus() {
        Review incoming = Review.builder()
                .productId(PRODUCT_ID)
                .rating(4)
                .title("Good")
                .comment("Nice product")
                .build();
        when(eligibilityRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        Review result = reviewService.addReview(USER_ID, incoming);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(reviewRepository).save(incoming);
    }

    @Test
    void addReview_trimsUserIdBeforeEligibilityCheck() {
        Review incoming = Review.builder()
                .productId(PRODUCT_ID)
                .rating(4)
                .title("Good")
                .comment("Nice product")
                .build();
        when(eligibilityRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(true);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        Review result = reviewService.addReview("  " + USER_ID + "  ", incoming);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        verify(eligibilityRepository).existsByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    void addReview_blankUserId_throwsInvalidReviewRequestException() {
        assertThatThrownBy(() -> reviewService.addReview(" ", pendingReview(5)))
                .isInstanceOf(InvalidReviewRequestException.class)
                .hasMessage("Review userId is required");

        verifyNoInteractions(eligibilityRepository, reviewRepository);
    }

    @Test
    void addReview_notEligible_throwsUnauthorizedReviewException() {
        Review incoming = Review.builder()
                .productId(PRODUCT_ID)
                .rating(5)
                .title("Love it")
                .comment("Amazing")
                .build();
        when(eligibilityRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.addReview(USER_ID, incoming))
                .isInstanceOf(UnauthorizedReviewException.class)
                .hasMessageContaining(USER_ID)
                .hasMessageContaining(PRODUCT_ID.toString());

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void addReview_missingProductId_throwsInvalidReviewRequestException() {
        Review incoming = Review.builder()
                .rating(5)
                .title("Love it")
                .build();

        assertThatThrownBy(() -> reviewService.addReview(USER_ID, incoming))
                .isInstanceOf(InvalidReviewRequestException.class)
                .hasMessageContaining("productId is required");

        verifyNoInteractions(eligibilityRepository, reviewRepository);
    }

    @Test
    void addReview_invalidRating_throwsInvalidReviewRequestException() {
        Review incoming = Review.builder()
                .productId(PRODUCT_ID)
                .rating(6)
                .title("Love it")
                .build();

        assertThatThrownBy(() -> reviewService.addReview(USER_ID, incoming))
                .isInstanceOf(InvalidReviewRequestException.class)
                .hasMessageContaining("rating must be between 1 and 5");

        verifyNoInteractions(eligibilityRepository, reviewRepository);
    }

    @Test
    void addReview_blankTitle_throwsInvalidReviewRequestException() {
        Review incoming = Review.builder()
                .productId(PRODUCT_ID)
                .rating(5)
                .title(" ")
                .build();

        assertThatThrownBy(() -> reviewService.addReview(USER_ID, incoming))
                .isInstanceOf(InvalidReviewRequestException.class)
                .hasMessageContaining("title is required");

        verifyNoInteractions(eligibilityRepository, reviewRepository);
    }

    // ─── getApprovedReviews ───────────────────────────────────────────────────

    @Test
    void getApprovedReviews_returnsOnlyApproved() {
        List<Review> approved = List.of(approvedReview(5), approvedReview(4));
        when(reviewRepository.findByProductIdAndStatus(PRODUCT_ID, "APPROVED")).thenReturn(approved);

        List<Review> result = reviewService.getApprovedReviews(PRODUCT_ID);

        assertThat(result).hasSize(2);
        verify(reviewRepository).findByProductIdAndStatus(PRODUCT_ID, "APPROVED");
    }

    @Test
    void getApprovedReviews_nullProductId_throwsInvalidReviewRequestException() {
        assertThatThrownBy(() -> reviewService.getApprovedReviews(null))
                .isInstanceOf(InvalidReviewRequestException.class)
                .hasMessage("Review productId is required");

        verify(reviewRepository, never()).findByProductIdAndStatus(any(), any());
    }

    // ─── getProductSummary ────────────────────────────────────────────────────

    @Test
    void getProductSummary_noReviews_returnsZeroStats() {
        when(reviewRepository.findByProductIdAndStatus(PRODUCT_ID, "APPROVED")).thenReturn(List.of());

        ReviewSummary summary = reviewService.getProductSummary(PRODUCT_ID);

        assertThat(summary.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(summary.getTotalReviews()).isEqualTo(0);
        assertThat(summary.getAverageRating()).isEqualTo(0.0);
        assertThat(summary.getRatingDistribution()).containsKey(1);
    }

    @Test
    void getProductSummary_withReviews_calculatesAverageAndDistribution() {
        List<Review> reviews = List.of(approvedReview(4), approvedReview(5), approvedReview(3));
        when(reviewRepository.findByProductIdAndStatus(PRODUCT_ID, "APPROVED")).thenReturn(reviews);

        ReviewSummary summary = reviewService.getProductSummary(PRODUCT_ID);

        assertThat(summary.getTotalReviews()).isEqualTo(3);
        assertThat(summary.getAverageRating()).isEqualTo(4.0);
        assertThat(summary.getRatingDistribution().get(4)).isEqualTo(1L);
        assertThat(summary.getRatingDistribution().get(5)).isEqualTo(1L);
        assertThat(summary.getRatingDistribution().get(3)).isEqualTo(1L);
        assertThat(summary.getRatingDistribution().get(1)).isEqualTo(0L);
        assertThat(summary.getRatingDistribution().get(2)).isEqualTo(0L);
    }

    @Test
    void getProductSummary_averageRoundedToOneDecimal() {
        List<Review> reviews = List.of(approvedReview(4), approvedReview(5), approvedReview(5));
        when(reviewRepository.findByProductIdAndStatus(PRODUCT_ID, "APPROVED")).thenReturn(reviews);

        ReviewSummary summary = reviewService.getProductSummary(PRODUCT_ID);

        assertThat(summary.getAverageRating()).isEqualTo(4.7);
    }

    @Test
    void getProductSummary_nullProductId_throwsInvalidReviewRequestException() {
        assertThatThrownBy(() -> reviewService.getProductSummary(null))
                .isInstanceOf(InvalidReviewRequestException.class)
                .hasMessage("Review productId is required");

        verify(reviewRepository, never()).findByProductIdAndStatus(any(), any());
    }

    // ─── moderateReview ───────────────────────────────────────────────────────

    @Test
    void moderateReview_approvesReview() {
        Review review = pendingReview(5);
        review.setId(REVIEW_ID);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);

        Review result = reviewService.moderateReview(REVIEW_ID, "APPROVED");

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(reviewRepository).save(review);
    }

    @Test
    void moderateReview_rejectsReview() {
        Review review = pendingReview(2);
        review.setId(REVIEW_ID);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);

        Review result = reviewService.moderateReview(REVIEW_ID, "REJECTED");

        assertThat(result.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void moderateReview_notFound_throwsReviewNotFoundException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.moderateReview(REVIEW_ID, "APPROVED"))
                .isInstanceOf(ReviewNotFoundException.class)
                .hasMessageContaining(REVIEW_ID.toString());
    }

    @Test
    void moderateReview_invalidStatus_throwsInvalidReviewRequestException() {
        assertThatThrownBy(() -> reviewService.moderateReview(REVIEW_ID, "SPAM"))
                .isInstanceOf(InvalidReviewRequestException.class)
                .hasMessageContaining("APPROVED or REJECTED");

        verifyNoInteractions(reviewRepository);
    }

    @Test
    void moderateReview_nullReviewId_throwsInvalidReviewRequestException() {
        assertThatThrownBy(() -> reviewService.moderateReview(null, "APPROVED"))
                .isInstanceOf(InvalidReviewRequestException.class)
                .hasMessage("Review id is required");

        verifyNoInteractions(reviewRepository);
    }

    // ─── getUserReviews ───────────────────────────────────────────────────────

    @Test
    void getUserReviews_returnsAllReviewsByUser() {
        List<Review> reviews = List.of(pendingReview(4), approvedReview(5));
        when(reviewRepository.findByUserId(USER_ID)).thenReturn(reviews);

        List<Review> result = reviewService.getUserReviews(USER_ID);

        assertThat(result).hasSize(2);
        verify(reviewRepository).findByUserId(USER_ID);
    }

    @Test
    void getUserReviews_trimsUserIdBeforeLookup() {
        List<Review> reviews = List.of(pendingReview(4));
        when(reviewRepository.findByUserId(USER_ID)).thenReturn(reviews);

        List<Review> result = reviewService.getUserReviews("  " + USER_ID + "  ");

        assertThat(result).hasSize(1);
        verify(reviewRepository).findByUserId(USER_ID);
    }

    @Test
    void getUserReviews_blankUserId_throwsInvalidReviewRequestException() {
        assertThatThrownBy(() -> reviewService.getUserReviews(" "))
                .isInstanceOf(InvalidReviewRequestException.class)
                .hasMessage("Review userId is required");

        verify(reviewRepository, never()).findByUserId(any());
    }
}
