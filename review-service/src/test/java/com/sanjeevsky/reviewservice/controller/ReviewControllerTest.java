package com.sanjeevsky.reviewservice.controller;

import com.sanjeevsky.reviewservice.dto.ReviewSummary;
import com.sanjeevsky.reviewservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.reviewservice.model.Review;
import com.sanjeevsky.reviewservice.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    private static final String USER_ID = "buyer@example.com";
    private static final UUID REVIEW_ID = UUID.fromString("4203f642-65e0-4798-8c77-d2d279144cab");
    private static final UUID PRODUCT_ID = UUID.fromString("943c741c-f06d-4373-818e-095362b8cc2d");

    @Mock
    private ReviewService reviewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReviewController(reviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addReview_withXUser_returns201AndForwardsReview() throws Exception {
        when(reviewService.addReview(eq(USER_ID), any(Review.class))).thenReturn(review("PENDING"));

        mockMvc.perform(post("/review-service/review")
                        .header("X-User", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID
                                + "\",\"rating\":5,\"title\":\"Great\",\"comment\":\"Good quality\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Review submitted for moderation"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewService).addReview(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(captor.getValue().getRating()).isEqualTo(5);
        assertThat(captor.getValue().getTitle()).isEqualTo("Great");
    }

    @Test
    void addReview_invalidReview_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(post("/review-service/review")
                        .header("X-User", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("productId is required")));

        verifyNoInteractions(reviewService);
    }

    @Test
    void getApprovedReviews_forwardsProductId() throws Exception {
        when(reviewService.getApprovedReviews(PRODUCT_ID)).thenReturn(List.of(review("APPROVED")));

        mockMvc.perform(get("/review-service/review/product/{productId}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("APPROVED"));

        verify(reviewService).getApprovedReviews(PRODUCT_ID);
    }

    @Test
    void getProductSummary_forwardsProductId() throws Exception {
        when(reviewService.getProductSummary(PRODUCT_ID)).thenReturn(summary());

        mockMvc.perform(get("/review-service/review/product/{productId}/summary", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.averageRating").value(4.5));

        verify(reviewService).getProductSummary(PRODUCT_ID);
    }

    @Test
    void moderateReview_forwardsIdAndStatus() throws Exception {
        when(reviewService.moderateReview(REVIEW_ID, "APPROVED")).thenReturn(review("APPROVED"));

        mockMvc.perform(put("/review-service/review/{id}/moderate", REVIEW_ID)
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Review moderated successfully"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        verify(reviewService).moderateReview(REVIEW_ID, "APPROVED");
    }

    @Test
    void getUserReviews_forwardsXUser() throws Exception {
        when(reviewService.getUserReviews(USER_ID)).thenReturn(List.of(review("PENDING")));

        mockMvc.perform(get("/review-service/review/my")
                        .header("X-User", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(USER_ID));

        verify(reviewService).getUserReviews(USER_ID);
    }

    private Review review(String status) {
        return Review.builder()
                .id(REVIEW_ID)
                .productId(PRODUCT_ID)
                .userId(USER_ID)
                .rating(5)
                .title("Great")
                .comment("Good quality")
                .status(status)
                .build();
    }

    private ReviewSummary summary() {
        return ReviewSummary.builder()
                .productId(PRODUCT_ID)
                .averageRating(4.5)
                .totalReviews(2)
                .ratingDistribution(Map.of(4, 1L, 5, 1L))
                .build();
    }
}
