package com.sanjeevsky.couponservice.controller;

import com.sanjeevsky.couponservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.couponservice.model.Coupon;
import com.sanjeevsky.couponservice.service.CouponService;
import com.sanjeevsky.platform.security.AdminAuthorizationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CouponAdminAuthorizationTest {

    private static final UUID COUPON_ID = UUID.fromString("65019664-907f-4f01-a309-bfd4067f55be");

    @Mock
    private CouponService couponService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CouponController(couponService))
                .addInterceptors(new AdminAuthorizationInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createCoupon_customerRoleReturns403() throws Exception {
        mockMvc.perform(post("/coupon-service/coupon")
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE10\",\"type\":\"PERCENTAGE\",\"value\":10}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(couponService);
    }

    @Test
    void getAllCoupons_withoutRoleReturns403() throws Exception {
        mockMvc.perform(get("/coupon-service/admin/coupons"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(couponService);
    }

    @Test
    void getAllCoupons_adminRoleReachesService() throws Exception {
        when(couponService.getAllCoupons()).thenReturn(List.of());

        mockMvc.perform(get("/coupon-service/admin/coupons")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        verify(couponService).getAllCoupons();
    }

    @Test
    void createCoupon_adminRoleReachesService() throws Exception {
        when(couponService.createCoupon(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/coupon-service/coupon")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE10\",\"type\":\"PERCENTAGE\",\"value\":10}"))
                .andExpect(status().isCreated());

        verify(couponService).createCoupon(any(Coupon.class));
    }

    @Test
    void setCouponActive_customerRoleReturns403() throws Exception {
        mockMvc.perform(put("/coupon-service/coupon/{couponId}/active", COUPON_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .param("active", "false"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(couponService);
    }

    @Test
    void setCouponActive_adminRoleReachesService() throws Exception {
        Coupon coupon = Coupon.builder().id(COUPON_ID).code("SAVE10").type("PERCENTAGE").value(10).build();
        when(couponService.setCouponActive(COUPON_ID, false)).thenReturn(coupon);

        mockMvc.perform(put("/coupon-service/coupon/{couponId}/active", COUPON_ID)
                        .header("X-User-Role", "ADMIN")
                        .param("active", "false"))
                .andExpect(status().isOk());

        verify(couponService).setCouponActive(COUPON_ID, false);
    }
}
