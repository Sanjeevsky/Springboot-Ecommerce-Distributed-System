package com.sanjeevsky.shoppingcartservice.exceptions;

public class CartNotFoundException extends RuntimeException {

    public CartNotFoundException(String message) {
        super(message);
    }
}
