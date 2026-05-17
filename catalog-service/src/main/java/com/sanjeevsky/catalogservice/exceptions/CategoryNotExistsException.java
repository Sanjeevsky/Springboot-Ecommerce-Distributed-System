package com.sanjeevsky.catalogservice.exceptions;

public class CategoryNotExistsException extends RuntimeException {
    public CategoryNotExistsException(String message) {
        super(message);
    }
}
