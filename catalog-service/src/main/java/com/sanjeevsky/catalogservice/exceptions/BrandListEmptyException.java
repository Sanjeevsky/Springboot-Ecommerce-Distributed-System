package com.sanjeevsky.catalogservice.exceptions;

public class BrandListEmptyException extends RuntimeException {
    public BrandListEmptyException(String message) {
        super(message);
    }
}
