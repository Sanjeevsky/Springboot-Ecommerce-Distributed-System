package com.sanjeevsky.authserver.exceptions;

public class NoSuchUserExistsException extends Exception{
    public NoSuchUserExistsException(String message){
        super(message);
    }
}
