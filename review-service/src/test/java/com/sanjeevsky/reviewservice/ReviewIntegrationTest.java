package com.sanjeevsky.reviewservice;

import com.sanjeevsky.reviewservice.kafka.OrderEventConsumer;
import com.sanjeevsky.reviewservice.model.OrderEligibility;
import com.sanjeevsky.reviewservice.repository.OrderEligibilityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "eureka.client.enabled=false",
                "spring.boot.admin.client.enabled=false",
                "spring.zipkin.enabled=false",
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=",
                "spring.kafka.bootstrap-servers=localhost:9999",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "spring.datasource.url=jdbc:h2:mem:review-integration-db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@AutoConfigureMockMvc
class ReviewIntegrationTest {

    @MockBean
    OrderEventConsumer orderEventConsumer;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderEligibilityRepository eligibilityRepository;

    private static final UUID PRODUCT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private void grantEligibility(String userId, UUID productId) {
        eligibilityRepository.save(OrderEligibility.builder()
                .userId(userId)
                .productId(productId)
                .orderId(UUID.randomUUID())
                .build());
    }

    private String reviewBody(UUID productId, int rating) {
        return "{\"productId\":\"" + productId + "\",\"rating\":" + rating
                + ",\"title\":\"Great\",\"comment\":\"Really good\"}";
    }

    private String extractId(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        int start = json.indexOf("\"id\":\"") + 6;
        int end   = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    // ─── addReview ────────────────────────────────────────────────────────────

    @Test
    void addReview_eligible_returns201WithPendingStatus() throws Exception {
        String user = "reviewer1@example.com";
        grantEligibility(user, PRODUCT_A);

        mockMvc.perform(post("/review-service/review")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(PRODUCT_A, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.userId").value(user))
                .andExpect(jsonPath("$.data.rating").value(5));
    }

    @Test
    void addReview_notEligible_returns403() throws Exception {
        mockMvc.perform(post("/review-service/review")
                        .header("X-User", "noorder@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(PRODUCT_B, 4)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addReview_invalidRating_returns400() throws Exception {
        String user = "invalid-rating@example.com";
        grantEligibility(user, PRODUCT_A);

        mockMvc.perform(post("/review-service/review")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(PRODUCT_A, 6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("rating must be at most 5")));
    }

    // ─── getApprovedReviews ───────────────────────────────────────────────────

    @Test
    void getApprovedReviews_returnsOnlyApproved() throws Exception {
        String user = "reviewer2@example.com";
        grantEligibility(user, PRODUCT_A);

        MvcResult added = mockMvc.perform(post("/review-service/review")
                        .header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(PRODUCT_A, 4)))
                .andExpect(status().isCreated())
                .andReturn();
        String reviewId = extractId(added);

        // Before approval: no approved reviews
        mockMvc.perform(get("/review-service/review/product/" + PRODUCT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.status=='APPROVED')]").doesNotExist());

        // Approve and verify
        mockMvc.perform(put("/review-service/review/" + reviewId + "/moderate")
                        .param("status", "APPROVED"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/review-service/review/product/" + PRODUCT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data[0].rating").value(4));
    }

    // ─── getProductSummary ────────────────────────────────────────────────────

    @Test
    void getProductSummary_noReviews_returnsZeroStats() throws Exception {
        mockMvc.perform(get("/review-service/review/product/" + PRODUCT_B + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReviews").value(0))
                .andExpect(jsonPath("$.data.averageRating").value(0.0));
    }

    @Test
    void getProductSummary_withApprovedReviews_calculatesAverage() throws Exception {
        UUID prod = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String u1 = "s1@example.com";
        String u2 = "s2@example.com";
        grantEligibility(u1, prod);
        grantEligibility(u2, prod);

        MvcResult r1 = mockMvc.perform(post("/review-service/review")
                        .header("X-User", u1).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(prod, 4))).andReturn();
        MvcResult r2 = mockMvc.perform(post("/review-service/review")
                        .header("X-User", u2).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(prod, 2))).andReturn();

        mockMvc.perform(put("/review-service/review/" + extractId(r1) + "/moderate")
                .param("status", "APPROVED")).andExpect(status().isOk());
        mockMvc.perform(put("/review-service/review/" + extractId(r2) + "/moderate")
                .param("status", "APPROVED")).andExpect(status().isOk());

        mockMvc.perform(get("/review-service/review/product/" + prod + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReviews").value(2))
                .andExpect(jsonPath("$.data.averageRating").value(3.0));
    }

    // ─── moderateReview ───────────────────────────────────────────────────────

    @Test
    void moderateReview_reject_setsRejectedStatus() throws Exception {
        String user = "reviewer3@example.com";
        UUID prod = UUID.fromString("44444444-4444-4444-4444-444444444444");
        grantEligibility(user, prod);

        MvcResult added = mockMvc.perform(post("/review-service/review")
                        .header("X-User", user).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(prod, 1))).andReturn();

        mockMvc.perform(put("/review-service/review/" + extractId(added) + "/moderate")
                        .param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void moderateReview_notFound_returns404() throws Exception {
        mockMvc.perform(put("/review-service/review/" + UUID.randomUUID() + "/moderate")
                        .param("status", "APPROVED"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moderateReview_invalidStatus_returns400() throws Exception {
        mockMvc.perform(put("/review-service/review/" + UUID.randomUUID() + "/moderate")
                        .param("status", "SPAM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("APPROVED or REJECTED")));
    }

    // ─── getUserReviews ───────────────────────────────────────────────────────

    @Test
    void getUserReviews_returnsAllForUser() throws Exception {
        String user = "myreviewer@example.com";
        UUID pA = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID pB = UUID.fromString("77777777-7777-7777-7777-777777777777");
        grantEligibility(user, pA);
        grantEligibility(user, pB);

        mockMvc.perform(post("/review-service/review")
                .header("X-User", user).contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody(pA, 5))).andExpect(status().isCreated());
        mockMvc.perform(post("/review-service/review")
                .header("X-User", user).contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody(pB, 3))).andExpect(status().isCreated());

        mockMvc.perform(get("/review-service/review/my").header("X-User", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
