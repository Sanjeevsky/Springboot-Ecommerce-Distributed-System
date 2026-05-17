package com.sanjeevsky.catalogservice.exceptions;

public class SubCategoryListEmptyException extends RuntimeException {
    public SubCategoryListEmptyException(String message) {
        super(message);
    }
}
