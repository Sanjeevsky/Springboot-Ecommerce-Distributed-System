package com.sanjeevsky.authserver.exceptions;

public class CredentialsMismatchException extends Exception{
    public CredentialsMismatchException(String message) {
        super(message);
    }
}
