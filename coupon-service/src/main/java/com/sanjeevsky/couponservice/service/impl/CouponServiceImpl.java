package com.sanjeevsky.couponservice.service.impl;

import com.sanjeevsky.couponservice.exceptions.CouponNotFoundException;
import com.sanjeevsky.couponservice.exceptions.InvalidCouponException;
import com.sanjeevsky.couponservice.model.Coupon;
import com.sanjeevsky.couponservice.model.CouponValidationResult;
import com.sanjeevsky.couponservice.repository.CouponRepository;
import com.sanjeevsky.couponservice.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponRepository couponRepository;

    @Override
    public Coupon createCoupon(Coupon coupon) {
        log.info("Creating coupon with code: {}", coupon.getCode());
        couponRepository.findByCode(coupon.getCode()).ifPresent(existing -> {
            throw new InvalidCouponException("Coupon code already exists: " + coupon.getCode());
        });
        Coupon saved = couponRepository.save(coupon);
        log.info("Coupon created with id: {}", saved.getId());
        return saved;
    }

    @Override
    public CouponValidationResult validateCoupon(String code, double orderAmount) {
        log.info("Validating coupon code: {} for orderAmount: {}", code, orderAmount);

        Coupon coupon = couponRepository.findByCode(code)
                .orElse(null);

        if (coupon == null) {
            return CouponValidationResult.builder()
                    .valid(false)
                    .message("Coupon not found: " + code)
                    .discountAmount(0)
                    .couponCode(code)
                    .build();
        }

        if (!coupon.isActive()) {
            return CouponValidationResult.builder()
                    .valid(false)
                    .message("Coupon is not active")
                    .discountAmount(0)
                    .couponCode(code)
                    .build();
        }

        if (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(LocalDate.now())) {
            return CouponValidationResult.builder()
                    .valid(false)
                    .message("Coupon has expired")
                    .discountAmount(0)
                    .couponCode(code)
                    .build();
        }

        if (coupon.getMaxUsageCount() != -1 && coupon.getUsedCount() >= coupon.getMaxUsageCount()) {
            return CouponValidationResult.builder()
                    .valid(false)
                    .message("Coupon usage limit exceeded")
                    .discountAmount(0)
                    .couponCode(code)
                    .build();
        }

        if (orderAmount < coupon.getMinOrderAmount()) {
            return CouponValidationResult.builder()
                    .valid(false)
                    .message("Order amount is less than minimum required: " + coupon.getMinOrderAmount())
                    .discountAmount(0)
                    .couponCode(code)
                    .build();
        }

        double discountAmount;
        if ("PERCENTAGE".equalsIgnoreCase(coupon.getType())) {
            discountAmount = orderAmount * coupon.getValue() / 100.0;
        } else {
            discountAmount = coupon.getValue();
        }

        log.info("Coupon {} is valid, discount: {}", code, discountAmount);
        return CouponValidationResult.builder()
                .valid(true)
                .message("Coupon is valid")
                .discountAmount(discountAmount)
                .couponCode(code)
                .build();
    }

    @Override
    public Coupon applyCoupon(String code) {
        log.info("Applying coupon code: {}", code);
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + code));
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        Coupon updated = couponRepository.save(coupon);
        log.info("Coupon {} applied, usedCount now: {}", code, updated.getUsedCount());
        return updated;
    }

    @Override
    public List<Coupon> getActiveCoupons() {
        log.info("Fetching all active coupons");
        return couponRepository.findByActiveTrue();
    }
}
