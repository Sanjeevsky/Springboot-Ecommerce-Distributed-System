package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.CouponFeignClient;
import com.sanjeevsky.orderservice.model.CouponValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponFeignClientFallbackTest {

    private static final CouponFeignClient FALLBACK = new CouponFeignClientFallback()
            .create(null);

    @Test
    void validateCoupon_returnsInvalidResultWithoutDiscount() {
        CouponValidationResult result = FALLBACK.validateCoupon("SAVE10", 100.0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("unavailable");
        assertThat(result.getDiscountAmount()).isZero();
        assertThat(result.getCouponCode()).isEqualTo("SAVE10");
    }

    @Test
    void applyCoupon_noopsWhenCouponServiceUnavailable() {
        FALLBACK.applyCoupon("SAVE10");
    }
}
