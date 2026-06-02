package com.sanjeevsky.shoppingcartservice.exceptions;

public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message) {
        super(message);
    }
}
