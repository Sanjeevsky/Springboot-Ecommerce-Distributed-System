package com.sanjeevsky.couponservice.controller;

import com.sanjeevsky.couponservice.model.Coupon;
import com.sanjeevsky.couponservice.model.CouponValidationResult;
import com.sanjeevsky.couponservice.service.CouponService;
import com.sanjeevsky.platform.response.ApiResponse;
import com.sanjeevsky.platform.security.AdminOnly;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/coupon-service")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/coupon")
    @AdminOnly
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

    @GetMapping("/admin/coupons")
    @AdminOnly
    public ResponseEntity<ApiResponse<List<Coupon>>> getAllCoupons() {
        log.info("Received admin request to fetch all coupons");
        return ResponseEntity.ok(ApiResponse.ok(couponService.getAllCoupons()));
    }

    @PutMapping("/coupon/{couponId}/active")
    @AdminOnly
    public ResponseEntity<ApiResponse<Coupon>> setCouponActive(
            @PathVariable UUID couponId,
            @RequestParam boolean active) {
        log.info("Received admin request to set coupon {} active={}", couponId, active);
        return ResponseEntity.ok(ApiResponse.ok(
                active ? "Coupon activated" : "Coupon deactivated",
                couponService.setCouponActive(couponId, active)));
    }
}
