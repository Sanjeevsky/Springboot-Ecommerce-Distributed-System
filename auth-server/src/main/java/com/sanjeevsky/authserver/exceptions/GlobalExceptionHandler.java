package com.sanjeevsky.authserver.exceptions;

import com.sanjeevsky.platform.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchUserExistsException.class)
    public ResponseEntity<ApiResponse<Void>> noUserFoundExceptionHandler(NoSuchUserExistsException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> userAlreadyExistsExceptionHandler(UserAlreadyExistsException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CredentialsMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> credentialMismatchExceptionHandler(CredentialsMismatchException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.UNAUTHORIZED);
    }
}
