package com.sanjeevsky.catalogservice.exceptions;

public class CategoryAlreadyExistsException extends Exception {
    public CategoryAlreadyExistsException(String message) {
        super(message);
    }
}
