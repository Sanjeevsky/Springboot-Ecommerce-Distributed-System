package com.sanjeevsky.catalogservice.exceptions;

public class InvalidVariantRequestException extends RuntimeException {
    public InvalidVariantRequestException(String message) {
        super(message);
    }
}
