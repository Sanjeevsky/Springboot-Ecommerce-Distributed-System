package com.sanjeevsky.couponservice.exceptions;

public class InvalidCouponException extends RuntimeException {

    public InvalidCouponException(String message) {
        super(message);
    }
}
