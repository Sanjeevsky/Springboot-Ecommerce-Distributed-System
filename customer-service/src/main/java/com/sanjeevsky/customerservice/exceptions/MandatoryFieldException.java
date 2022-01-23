package com.sanjeevsky.customerservice.exceptions;

public class MandatoryFieldException extends RuntimeException{
    public MandatoryFieldException(String message) {
        super(message);
    }
}
