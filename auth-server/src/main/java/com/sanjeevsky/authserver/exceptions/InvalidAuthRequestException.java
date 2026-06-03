package com.sanjeevsky.authserver.exceptions;

public class InvalidAuthRequestException extends RuntimeException {

    public InvalidAuthRequestException(String message) {
        super(message);
    }
}
