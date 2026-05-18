package com.sanjeevsky.reviewservice.exceptions;

public class UnauthorizedReviewException extends RuntimeException {

    public UnauthorizedReviewException(String message) {
        super(message);
    }
}
