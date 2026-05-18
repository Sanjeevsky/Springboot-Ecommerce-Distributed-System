package com.sanjeevsky.wishlistservice.exceptions;

public class WishlistItemAlreadyExistsException extends RuntimeException {

    public WishlistItemAlreadyExistsException(String message) {
        super(message);
    }
}
