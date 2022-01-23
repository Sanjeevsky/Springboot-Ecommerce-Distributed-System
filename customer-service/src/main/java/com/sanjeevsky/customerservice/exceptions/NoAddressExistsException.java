package com.sanjeevsky.customerservice.exceptions;

public class NoAddressExistsException extends RuntimeException {
    public NoAddressExistsException(String message) {
        super(message);
    }
}
