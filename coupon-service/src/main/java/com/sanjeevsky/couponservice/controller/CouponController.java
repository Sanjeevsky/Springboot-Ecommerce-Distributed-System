package com.sanjeevsky.couponservice.controller;

import com.sanjeevsky.couponservice.model.Coupon;
import com.sanjeevsky.couponservice.model.CouponValidationResult;
import com.sanjeevsky.couponservice.service.CouponService;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/coupon-service")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/coupon")
    public ResponseEntity<ApiResponse<Coupon>> createCoupon(@RequestBody @Valid Coupon coupon) {
        log.info("Received request to create coupon with code: {}", coupon.getCode());
        return new ResponseEntity<>(ApiResponse.ok("Coupon created successfully", couponService.createCoupon(coupon)), HttpStatus.CREATED);
    }

    @GetMapping("/coupon/validate")
    public ResponseEntity<ApiResponse<CouponValidationResult>> validateCoupon(
            @RequestParam("code") String code,
            @RequestParam("amount") double amount) {
        log.info("Received request to validate coupon: {} for amount: {}", code, amount);
        return ResponseEntity.ok(ApiResponse.ok(couponService.validateCoupon(code, amount)));
    }

    @PostMapping("/coupon/apply")
    public ResponseEntity<ApiResponse<Coupon>> applyCoupon(@RequestParam("code") String code) {
        log.info("Received request to apply coupon: {}", code);
        return ResponseEntity.ok(ApiResponse.ok("Coupon applied successfully", couponService.applyCoupon(code)));
    }

    @GetMapping("/coupons")
    public ResponseEntity<ApiResponse<List<Coupon>>> getActiveCoupons() {
        log.info("Received request to fetch all active coupons");
        return ResponseEntity.ok(ApiResponse.ok(couponService.getActiveCoupons()));
    }
}
