package com.sanjeevsky.catalogservice.exceptions;

public class NoSuchProductExistsException extends Exception {
    public NoSuchProductExistsException(String message) {
        super(message);
    }
}
