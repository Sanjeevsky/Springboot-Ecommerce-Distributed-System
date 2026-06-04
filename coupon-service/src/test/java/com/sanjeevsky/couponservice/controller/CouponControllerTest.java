package com.sanjeevsky.couponservice.controller;

import com.sanjeevsky.couponservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.couponservice.model.Coupon;
import com.sanjeevsky.couponservice.model.CouponValidationResult;
import com.sanjeevsky.couponservice.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    private static final UUID COUPON_ID = UUID.fromString("60e8c128-76b2-42aa-84a5-c12a2a87b0f3");

    @Mock
    private CouponService couponService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CouponController(couponService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createCoupon_validRequest_returns201AndForwardsCoupon() throws Exception {
        when(couponService.createCoupon(any(Coupon.class))).thenReturn(coupon());

        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE10\",\"type\":\"PERCENTAGE\",\"value\":10.0,"
                                + "\"minOrderAmount\":100.0,\"maxUsageCount\":5,\"usedCount\":0,"
                                + "\"expiryDate\":\"2099-12-31\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Coupon created successfully"))
                .andExpect(jsonPath("$.data.code").value("SAVE10"));

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponService).createCoupon(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("SAVE10");
        assertThat(captor.getValue().getType()).isEqualTo("PERCENTAGE");
        assertThat(captor.getValue().getValue()).isEqualTo(10.0);
    }

    @Test
    void createCoupon_invalidRequest_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("code is required")));

        verifyNoInteractions(couponService);
    }

    @Test
    void validateCoupon_forwardsCodeAndAmount() throws Exception {
        when(couponService.validateCoupon("SAVE10", 200.0)).thenReturn(validationResult());

        mockMvc.perform(get("/coupon-service/coupon/validate")
                        .param("code", "SAVE10")
                        .param("amount", "200.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.discountAmount").value(20.0));

        verify(couponService).validateCoupon("SAVE10", 200.0);
    }

    @Test
    void applyCoupon_forwardsCodeAndReturnsUpdatedCoupon() throws Exception {
        Coupon applied = coupon();
        applied.setUsedCount(1);
        when(couponService.applyCoupon("SAVE10")).thenReturn(applied);

        mockMvc.perform(post("/coupon-service/coupon/apply")
                        .param("code", "SAVE10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Coupon applied successfully"))
                .andExpect(jsonPath("$.data.usedCount").value(1));

        verify(couponService).applyCoupon("SAVE10");
    }

    @Test
    void getActiveCoupons_returnsServiceList() throws Exception {
        when(couponService.getActiveCoupons()).thenReturn(List.of(coupon()));

        mockMvc.perform(get("/coupon-service/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(COUPON_ID.toString()));

        verify(couponService).getActiveCoupons();
    }

    private Coupon coupon() {
        return Coupon.builder()
                .id(COUPON_ID)
                .code("SAVE10")
                .type("PERCENTAGE")
                .value(10.0)
                .minOrderAmount(100.0)
                .maxUsageCount(5)
                .usedCount(0)
                .expiryDate(LocalDate.of(2099, 12, 31))
                .active(true)
                .build();
    }

    private CouponValidationResult validationResult() {
        return CouponValidationResult.builder()
                .valid(true)
                .message("Coupon applied")
                .discountAmount(20.0)
                .couponCode("SAVE10")
                .build();
    }
}
