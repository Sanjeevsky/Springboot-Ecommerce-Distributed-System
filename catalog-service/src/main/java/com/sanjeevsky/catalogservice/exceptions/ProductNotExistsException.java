package com.sanjeevsky.catalogservice.exceptions;

public class ProductNotExistsException extends RuntimeException {
    public ProductNotExistsException(String message) {
        super(message);
    }
}
