package com.sanjeevsky.catalogservice.exceptions;

public class CategoryNotExistsException extends Exception {
    public CategoryNotExistsException(String message) {
        super(message);
    }
}
