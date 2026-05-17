package com.sanjeevsky.catalogservice.exceptions;

public class SubCategoryNotExistsException extends RuntimeException {
    public SubCategoryNotExistsException(String message) {
        super(message);
    }
}
