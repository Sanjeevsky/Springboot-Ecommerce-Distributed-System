package com.sanjeevsky.customerservice.exceptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MandatoryFieldException.class)
    public ResponseEntity<?> exception(MandatoryFieldException exception){
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AddressAlreadyExistsException.class)
    public ResponseEntity<?> exception(AddressAlreadyExistsException exception){
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.CONFLICT);
    }
    @ExceptionHandler(AddressDoesnotExistsException.class)
    public ResponseEntity<?> exception(AddressDoesnotExistsException exception){
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.CONFLICT);
    }
    @ExceptionHandler(NoAddressExistsException.class)
    public ResponseEntity<?> exception(NoAddressExistsException exception){
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.CONFLICT);
    }
}
