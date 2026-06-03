package com.sanjeevsky.couponservice.service;

import com.sanjeevsky.couponservice.exceptions.CouponNotFoundException;
import com.sanjeevsky.couponservice.exceptions.InvalidCouponException;
import com.sanjeevsky.couponservice.model.Coupon;
import com.sanjeevsky.couponservice.model.CouponValidationResult;
import com.sanjeevsky.couponservice.repository.CouponRepository;
import com.sanjeevsky.couponservice.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponServiceImpl couponService;

    private static final String CODE = "SAVE20";

    private Coupon activeCoupon() {
        return Coupon.builder()
                .id(UUID.randomUUID())
                .code(CODE)
                .type("PERCENTAGE")
                .value(20.0)
                .minOrderAmount(100.0)
                .maxUsageCount(10)
                .usedCount(0)
                .active(true)
                .build();
    }

    // ─── createCoupon ─────────────────────────────────────────────────────────

    @Test
    void createCoupon_newCode_savesAndReturns() {
        Coupon coupon = activeCoupon();
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.empty());
        when(couponRepository.save(coupon)).thenReturn(coupon);

        Coupon result = couponService.createCoupon(coupon);

        assertThat(result).isSameAs(coupon);
        verify(couponRepository).save(coupon);
    }

    @Test
    void createCoupon_duplicateCode_throwsInvalidCouponException() {
        Coupon coupon = activeCoupon();
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.createCoupon(coupon))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessageContaining(CODE);

        verify(couponRepository, never()).save(any());
    }

    @Test
    void createCoupon_percentageAbove100_throwsInvalidCouponException() {
        Coupon coupon = activeCoupon();
        coupon.setValue(150.0);

        assertThatThrownBy(() -> couponService.createCoupon(coupon))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessageContaining("must not exceed 100");

        verifyNoInteractions(couponRepository);
    }

    @Test
    void createCoupon_negativeValue_throwsInvalidCouponException() {
        Coupon coupon = activeCoupon();
        coupon.setValue(-1.0);

        assertThatThrownBy(() -> couponService.createCoupon(coupon))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessageContaining("value must be greater than zero");

        verifyNoInteractions(couponRepository);
    }

    // ─── validateCoupon ───────────────────────────────────────────────────────

    @Test
    void validateCoupon_notFound_returnsInvalidResult() {
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.empty());

        CouponValidationResult result = couponService.validateCoupon(CODE, 200.0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).containsIgnoringCase("not found");
    }

    @Test
    void validateCoupon_inactive_returnsInvalidResult() {
        Coupon coupon = activeCoupon();
        coupon.setActive(false);
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));

        CouponValidationResult result = couponService.validateCoupon(CODE, 200.0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).containsIgnoringCase("not active");
    }

    @Test
    void validateCoupon_expired_returnsInvalidResult() {
        Coupon coupon = activeCoupon();
        coupon.setExpiryDate(LocalDate.now().minusDays(1));
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));

        CouponValidationResult result = couponService.validateCoupon(CODE, 200.0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).containsIgnoringCase("expired");
    }

    @Test
    void validateCoupon_usageLimitExceeded_returnsInvalidResult() {
        Coupon coupon = activeCoupon();
        coupon.setUsedCount(10);
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));

        CouponValidationResult result = couponService.validateCoupon(CODE, 200.0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).containsIgnoringCase("limit");
    }

    @Test
    void validateCoupon_belowMinOrderAmount_returnsInvalidResult() {
        Coupon coupon = activeCoupon();
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));

        CouponValidationResult result = couponService.validateCoupon(CODE, 50.0);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).containsIgnoringCase("minimum");
    }

    @Test
    void validateCoupon_percentage_calculatesDiscountCorrectly() {
        Coupon coupon = activeCoupon(); // 20% off, min 100
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));

        CouponValidationResult result = couponService.validateCoupon(CODE, 500.0);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getDiscountAmount()).isEqualTo(100.0);
    }

    @Test
    void validateCoupon_fixed_calculatesDiscountCorrectly() {
        Coupon coupon = activeCoupon();
        coupon.setType("FIXED");
        coupon.setValue(50.0);
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));

        CouponValidationResult result = couponService.validateCoupon(CODE, 200.0);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getDiscountAmount()).isEqualTo(50.0);
    }

    @Test
    void validateCoupon_unlimitedUsage_isAlwaysValid() {
        Coupon coupon = activeCoupon();
        coupon.setMaxUsageCount(-1);
        coupon.setUsedCount(999);
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));

        CouponValidationResult result = couponService.validateCoupon(CODE, 200.0);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validateCoupon_negativeOrderAmount_throwsInvalidCouponException() {
        assertThatThrownBy(() -> couponService.validateCoupon(CODE, -1.0))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessageContaining("Order amount must not be negative");

        verifyNoInteractions(couponRepository);
    }

    @Test
    void validateCoupon_blankCode_throwsInvalidCouponException() {
        assertThatThrownBy(() -> couponService.validateCoupon("  ", 100.0))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessageContaining("Coupon code is required");

        verifyNoInteractions(couponRepository);
    }

    @Test
    void validateCoupon_trimsCode_returnsNormalizedCouponCode() {
        Coupon coupon = activeCoupon();
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));

        CouponValidationResult result = couponService.validateCoupon("  " + CODE + "  ", 200.0);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getCouponCode()).isEqualTo(CODE);
        verify(couponRepository).findByCode(CODE);
    }

    // ─── applyCoupon ──────────────────────────────────────────────────────────

    @Test
    void applyCoupon_incrementsUsedCount() {
        Coupon coupon = activeCoupon();
        coupon.setUsedCount(3);
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));
        when(couponRepository.save(coupon)).thenReturn(coupon);

        couponService.applyCoupon(CODE);

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedCount()).isEqualTo(4);
    }

    @Test
    void applyCoupon_notFound_throwsCouponNotFoundException() {
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.applyCoupon(CODE))
                .isInstanceOf(CouponNotFoundException.class)
                .hasMessageContaining(CODE);
    }

    @Test
    void applyCoupon_blankCode_throwsInvalidCouponException() {
        assertThatThrownBy(() -> couponService.applyCoupon("  "))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessageContaining("Coupon code is required");

        verifyNoInteractions(couponRepository);
    }

    // ─── getActiveCoupons ─────────────────────────────────────────────────────

    @Test
    void getActiveCoupons_returnsActiveCoupons() {
        List<Coupon> coupons = List.of(activeCoupon(), activeCoupon());
        when(couponRepository.findByActiveTrue()).thenReturn(coupons);

        List<Coupon> result = couponService.getActiveCoupons();

        assertThat(result).hasSize(2);
        verify(couponRepository).findByActiveTrue();
    }
}
