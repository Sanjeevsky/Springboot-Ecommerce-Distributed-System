package com.sanjeevsky.authserver.exceptions;

public class NoSuchUserExistsException extends RuntimeException {
    public NoSuchUserExistsException(String message){
        super(message);
    }
}
