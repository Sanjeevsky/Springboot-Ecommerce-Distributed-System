package com.sanjeevsky.orderservice.clients.fallback;

import com.sanjeevsky.orderservice.clients.CouponFeignClient;
import com.sanjeevsky.orderservice.model.CouponValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CouponFeignClientFallback implements FallbackFactory<CouponFeignClient> {

    @Override
    public CouponFeignClient create(Throwable cause) {
        log.warn("Coupon service unavailable, skipping coupon validation", cause);
        return new CouponFeignClient() {
            @Override
            public CouponValidationResult validateCoupon(String code, double amount) {
                return new CouponValidationResult(false, "Coupon service unavailable", 0, code);
            }

            @Override
            public void applyCoupon(String code) {
                log.warn("Coupon service unavailable, skipping apply for code={}", code);
            }
        };
    }
}
