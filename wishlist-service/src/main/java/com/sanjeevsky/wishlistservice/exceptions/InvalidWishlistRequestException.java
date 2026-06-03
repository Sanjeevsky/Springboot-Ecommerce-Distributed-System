package com.sanjeevsky.wishlistservice.exceptions;

public class InvalidWishlistRequestException extends RuntimeException {
    public InvalidWishlistRequestException(String message) {
        super(message);
    }
}
