package com.sanjeevsky.catalogservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CategoryListEmptyException.class)
    public ResponseEntity<?> exceptionHandler(CategoryListEmptyException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NoSuchProductExistsException.class)
    public ResponseEntity<?> exceptionHandler(NoSuchProductExistsException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BrandAlreadyExistsException.class)
    public ResponseEntity<?> exceptionHandler(BrandAlreadyExistsException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SubCategoryListEmptyException.class)
    public ResponseEntity<?> exceptionHandler(SubCategoryListEmptyException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BrandListEmptyException.class)
    public ResponseEntity<?> exceptionHandler(BrandListEmptyException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(VariantNotExistsException.class)
    public ResponseEntity<?> exceptionHandler(VariantNotExistsException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ResponseEntity<?> exceptionHandler(CategoryAlreadyExistsException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BrandNotExistsException.class)
    public ResponseEntity<?> brandNotExistsExceptionHandler(BrandNotExistsException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CategoryNotExistsException.class)
    public ResponseEntity<?> categoryNotExistsExceptionHandler(CategoryNotExistsException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ResponseEntity<?> productAlreadyExistsExceptionHandler(ProductAlreadyExistsException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SubCategoryNotExistsException.class)
    public ResponseEntity<?> subCategoryNotExistsExceptionHandler(SubCategoryNotExistsException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ProductNotExistsException.class)
    public ResponseEntity<?> productNotExistsExceptionHandler(ProductNotExistsException exception) {
        return new ResponseEntity<String>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler
    public ResponseEntity<?> globalException(Exception exception) {
        return new ResponseEntity<String>(exception.getLocalizedMessage(), HttpStatus.CONFLICT);
    }
}
