package com.sanjeevsky.shoppingcartservice.exceptions;

public class InvalidCartRequestException extends RuntimeException {

    public InvalidCartRequestException(String message) {
        super(message);
    }
}
