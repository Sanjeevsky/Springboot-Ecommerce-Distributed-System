package com.sanjeevsky.customerservice.exceptions;

public class AddressDoesnotExistsException extends RuntimeException {
    public AddressDoesnotExistsException(String message) {
        super(message);
    }
}
