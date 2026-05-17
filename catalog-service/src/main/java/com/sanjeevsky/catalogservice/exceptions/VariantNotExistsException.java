package com.sanjeevsky.catalogservice.exceptions;

public class VariantNotExistsException extends RuntimeException {
    public VariantNotExistsException(String message) {
        super(message);
    }
}
