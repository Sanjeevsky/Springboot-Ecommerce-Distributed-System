package com.sanjeevsky.inventoryservice.exceptions;

public class InvalidInventoryRequestException extends RuntimeException {

    public InvalidInventoryRequestException(String message) {
        super(message);
    }
}
