package com.sanjeevsky.reviewservice.exceptions;

public class InvalidReviewRequestException extends RuntimeException {

    public InvalidReviewRequestException(String message) {
        super(message);
    }
}
