package com.sanjeevsky.authserver.exceptions;

import brave.Response;
import com.sanjeevsky.authserver.modal.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NoSuchUserExistsException.class)
    public ResponseEntity<?> noUserFoundExceptionHandler(NoSuchUserExistsException exception){
        return new ResponseEntity<String>(exception.getMessage(),HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<?> userAlreadyExistsExceptionHandler(UserAlreadyExistsException exception){
        return new ResponseEntity<String>(exception.getMessage(),HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(CredentialsMismatchException.class)
    public ResponseEntity<?> credentialMismatchExceptionHandler(CredentialsMismatchException exception){
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.CONFLICT);
    }
}
