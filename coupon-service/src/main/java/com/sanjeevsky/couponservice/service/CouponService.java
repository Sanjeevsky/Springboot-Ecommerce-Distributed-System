package com.sanjeevsky.couponservice.service;

import com.sanjeevsky.couponservice.model.Coupon;
import com.sanjeevsky.couponservice.model.CouponValidationResult;

import java.util.List;

public interface CouponService {

    Coupon createCoupon(Coupon coupon);

    CouponValidationResult validateCoupon(String code, double orderAmount);

    Coupon applyCoupon(String code);

    List<Coupon> getActiveCoupons();
}
