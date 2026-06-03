package com.sanjeevsky.couponservice.service.impl;

import com.sanjeevsky.couponservice.exceptions.CouponNotFoundException;
import com.sanjeevsky.couponservice.exceptions.InvalidCouponException;
import com.sanjeevsky.couponservice.model.Coupon;
import com.sanjeevsky.couponservice.model.CouponValidationResult;
import com.sanjeevsky.couponservice.repository.CouponRepository;
import com.sanjeevsky.couponservice.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    public CouponServiceImpl(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Override
    public Coupon createCoupon(Coupon coupon) {
        validateCouponForCreate(coupon);
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
        String normalizedCode = normalizeCouponCode(code);
        validateOrderAmount(orderAmount);
        log.info("Validating coupon code: {} for orderAmount: {}", normalizedCode, orderAmount);

        Coupon coupon = couponRepository.findByCode(normalizedCode)
                .orElse(null);

        if (coupon == null) {
            return CouponValidationResult.builder()
                    .valid(false)
                    .message("Coupon not found: " + normalizedCode)
                    .discountAmount(0)
                    .couponCode(normalizedCode)
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
        String normalizedCode = normalizeCouponCode(code);
        log.info("Applying coupon code: {}", normalizedCode);
        Coupon coupon = couponRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + normalizedCode));
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        Coupon updated = couponRepository.save(coupon);
        log.info("Coupon {} applied, usedCount now: {}", normalizedCode, updated.getUsedCount());
        return updated;
    }

    @Override
    public List<Coupon> getActiveCoupons() {
        log.info("Fetching all active coupons");
        return couponRepository.findByActiveTrue();
    }

    private void validateCouponForCreate(Coupon coupon) {
        if (coupon == null) {
            throw new InvalidCouponException("Coupon request is required");
        }
        coupon.setCode(normalizeCouponCode(coupon.getCode()));
        String normalizedType = coupon.getType() == null ? null : coupon.getType().trim().toUpperCase();
        if (normalizedType == null || normalizedType.isEmpty()) {
            throw new InvalidCouponException("Coupon type is required");
        }
        if (!"PERCENTAGE".equals(normalizedType) && !"FIXED".equals(normalizedType)) {
            throw new InvalidCouponException("Coupon type must be PERCENTAGE or FIXED");
        }
        coupon.setType(normalizedType);
        if (!Double.isFinite(coupon.getValue()) || Double.compare(coupon.getValue(), 0.0) <= 0) {
            throw new InvalidCouponException("Coupon value must be greater than zero");
        }
        if ("PERCENTAGE".equals(normalizedType) && Double.compare(coupon.getValue(), 100.0) > 0) {
            throw new InvalidCouponException("Percentage coupon value must not exceed 100");
        }
        if (!Double.isFinite(coupon.getMinOrderAmount()) || Double.compare(coupon.getMinOrderAmount(), 0.0) < 0) {
            throw new InvalidCouponException("Coupon minimum order amount must not be negative");
        }
        if (coupon.getMaxUsageCount() < -1) {
            throw new InvalidCouponException("Coupon max usage count must be -1 or greater");
        }
        if (coupon.getUsedCount() < 0) {
            throw new InvalidCouponException("Coupon used count must not be negative");
        }
    }

    private String normalizeCouponCode(String code) {
        String normalized = code == null ? null : code.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new InvalidCouponException("Coupon code is required");
        }
        return normalized;
    }

    private void validateOrderAmount(double orderAmount) {
        if (!Double.isFinite(orderAmount) || Double.compare(orderAmount, 0.0) < 0) {
            throw new InvalidCouponException("Order amount must not be negative");
        }
    }
}
