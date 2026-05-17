package com.sanjeevsky.catalogservice.exceptions;

public class CategoryListEmptyException extends RuntimeException {
    public CategoryListEmptyException(String message) {
        super(message);
    }
}
