package com.sanjeevsky.couponservice.service;

import com.sanjeevsky.couponservice.model.Coupon;
import com.sanjeevsky.couponservice.model.CouponValidationResult;

import java.util.List;
import java.util.UUID;

public interface CouponService {

    Coupon createCoupon(Coupon coupon);

    CouponValidationResult validateCoupon(String code, double orderAmount);

    Coupon applyCoupon(String code);

    List<Coupon> getActiveCoupons();

    List<Coupon> getAllCoupons();

    Coupon setCouponActive(UUID couponId, boolean active);
}
