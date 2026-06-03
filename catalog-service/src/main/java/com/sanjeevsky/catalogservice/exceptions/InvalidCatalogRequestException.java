package com.sanjeevsky.catalogservice.exceptions;

public class InvalidCatalogRequestException extends RuntimeException {
    public InvalidCatalogRequestException(String message) {
        super(message);
    }
}
