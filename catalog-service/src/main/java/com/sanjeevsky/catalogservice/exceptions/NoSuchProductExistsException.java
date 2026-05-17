package com.sanjeevsky.catalogservice.exceptions;

public class NoSuchProductExistsException extends RuntimeException {
    public NoSuchProductExistsException(String message) {
        super(message);
    }
}
