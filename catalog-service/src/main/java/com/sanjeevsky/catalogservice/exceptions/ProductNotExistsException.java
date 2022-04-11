package com.sanjeevsky.catalogservice.exceptions;

public class ProductNotExistsException extends Exception {
    public ProductNotExistsException(String message) {
        super(message);
    }
}
