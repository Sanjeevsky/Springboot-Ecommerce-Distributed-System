package com.sanjeevsky.couponservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
                "spring.datasource.url=jdbc:h2:mem:coupon-integration-db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@AutoConfigureMockMvc
class CouponIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private String percentageCoupon(String code) {
        return "{\"code\":\"" + code + "\",\"type\":\"PERCENTAGE\",\"value\":20.0,"
                + "\"minOrderAmount\":100.0,\"maxUsageCount\":10,\"expiryDate\":\"2099-12-31\",\"active\":true}";
    }

    private String fixedCoupon(String code) {
        return "{\"code\":\"" + code + "\",\"type\":\"FIXED\",\"value\":50.0,"
                + "\"minOrderAmount\":0.0,\"maxUsageCount\":-1,\"expiryDate\":\"2099-12-31\",\"active\":true}";
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    @Test
    void createCoupon_returns201() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageCoupon("SAVE20")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SAVE20"))
                .andExpect(jsonPath("$.data.type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.data.value").value(20.0));
    }

    @Test
    void createCoupon_duplicateCode_returns400() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageCoupon("DUPCODE")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageCoupon("DUPCODE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCoupon_percentageAbove100_returns400() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TOOBIG\",\"type\":\"PERCENTAGE\",\"value\":150.0,"
                                + "\"minOrderAmount\":0.0,\"maxUsageCount\":10,\"expiryDate\":\"2099-12-31\",\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must not exceed 100")));
    }

    // ─── Validate ─────────────────────────────────────────────────────────────

    @Test
    void validateCoupon_percentageType_returnsCorrectDiscount() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageCoupon("PCT20")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/coupon-service/coupon/validate")
                        .param("code", "PCT20")
                        .param("amount", "200.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.discountAmount").value(40.0))
                .andExpect(jsonPath("$.data.couponCode").value("PCT20"));
    }

    @Test
    void validateCoupon_fixedType_returnsCorrectDiscount() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedCoupon("FLAT50")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/coupon-service/coupon/validate")
                        .param("code", "FLAT50")
                        .param("amount", "300.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.discountAmount").value(50.0))
                .andExpect(jsonPath("$.data.couponCode").value("FLAT50"));
    }

    @Test
    void validateCoupon_belowMinOrder_returnsValidFalse() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageCoupon("MINORDER")))
                .andExpect(status().isCreated());

        // minOrderAmount=100, sending 50 → valid=false (no exception, service returns result)
        mockMvc.perform(get("/coupon-service/coupon/validate")
                        .param("code", "MINORDER")
                        .param("amount", "50.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.discountAmount").value(0.0));
    }

    @Test
    void validateCoupon_notFound_returnsValidFalse() throws Exception {
        // validateCoupon returns valid=false when coupon not found (no exception thrown)
        mockMvc.perform(get("/coupon-service/coupon/validate")
                        .param("code", "NOSUCHCODE")
                        .param("amount", "100.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false));
    }

    @Test
    void validateCoupon_negativeAmount_returns400() throws Exception {
        mockMvc.perform(get("/coupon-service/coupon/validate")
                        .param("code", "NOSUCHCODE")
                        .param("amount", "-1.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must not be negative")));
    }

    // ─── Apply ────────────────────────────────────────────────────────────────

    @Test
    void applyCoupon_incrementsUsedCount() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedCoupon("APPLY10")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/coupon-service/coupon/apply")
                        .param("code", "APPLY10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedCount").value(1))
                .andExpect(jsonPath("$.data.code").value("APPLY10"));
    }

    // ─── List active ─────────────────────────────────────────────────────────

    @Test
    void getActiveCoupons_returnsOnlyActive() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ACTIVEC\",\"type\":\"FIXED\",\"value\":10.0,"
                                + "\"expiryDate\":\"2099-12-31\",\"active\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"INACTIVEC\",\"type\":\"FIXED\",\"value\":5.0,"
                                + "\"expiryDate\":\"2099-12-31\",\"active\":false}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/coupon-service/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='ACTIVEC')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='INACTIVEC')]").doesNotExist());
    }
}
