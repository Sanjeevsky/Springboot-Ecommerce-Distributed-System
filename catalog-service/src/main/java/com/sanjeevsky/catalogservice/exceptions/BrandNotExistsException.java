package com.sanjeevsky.catalogservice.exceptions;

public class BrandNotExistsException extends RuntimeException {
    public BrandNotExistsException(String message) {
        super(message);
    }
}
