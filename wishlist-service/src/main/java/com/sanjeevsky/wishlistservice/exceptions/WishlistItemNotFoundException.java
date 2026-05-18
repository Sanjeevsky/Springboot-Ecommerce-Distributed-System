package com.sanjeevsky.wishlistservice.exceptions;

public class WishlistItemNotFoundException extends RuntimeException {

    public WishlistItemNotFoundException(String message) {
        super(message);
    }
}
