package com.sanjeevsky.customerservice.exceptions;

public class CartDoesnotExistsException extends RuntimeException {
    public CartDoesnotExistsException(String message) {
        super(message);
    }
}
