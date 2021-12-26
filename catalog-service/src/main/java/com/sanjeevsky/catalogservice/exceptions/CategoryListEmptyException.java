package com.sanjeevsky.catalogservice.exceptions;

public class CategoryListEmptyException extends Exception {
    public CategoryListEmptyException(String message) {
        super(message);
    }
}
