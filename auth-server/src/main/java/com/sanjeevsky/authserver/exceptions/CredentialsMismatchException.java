package com.sanjeevsky.authserver.exceptions;

public class CredentialsMismatchException extends RuntimeException {
    public CredentialsMismatchException(String message) {
        super(message);
    }
}
