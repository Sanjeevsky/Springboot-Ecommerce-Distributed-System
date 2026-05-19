package com.sanjeevsky.orderservice.clients;

import com.sanjeevsky.orderservice.clients.fallback.CouponFeignClientFallback;
import com.sanjeevsky.orderservice.model.CouponValidationResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "coupon-service", url = "${clients.coupon.url:}", fallbackFactory = CouponFeignClientFallback.class)
public interface CouponFeignClient {

    @GetMapping("/coupon-service/coupon/validate")
    CouponValidationResult validateCoupon(@RequestParam("code") String code, @RequestParam("amount") double amount);

    @PostMapping("/coupon-service/coupon/apply")
    void applyCoupon(@RequestParam("code") String code);
}
