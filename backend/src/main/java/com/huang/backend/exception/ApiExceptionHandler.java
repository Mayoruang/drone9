package com.huang.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for API errors
 */
@ControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Handle IllegalArgumentException - usually validation or business rule violations
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException ex) {
        ApiError apiError = ApiError.builder()
                .message(ex.getMessage())
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(ZonedDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle ResourceNotFoundException - when a requested resource is not found
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ApiError apiError = ApiError.builder()
                .message(ex.getMessage())
                .status(HttpStatus.NOT_FOUND.value())
                .timestamp(ZonedDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }
    
    /**
     * Handle BusinessException - business logic violations
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex) {
        ApiError apiError = ApiError.builder()
                .message(ex.getMessage())
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(ZonedDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle bean validation exceptions
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationError> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ValidationError validationError = ValidationError.builder()
                .message("Validation failed")
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(ZonedDateTime.now())
                .errors(errors)
                .build();
        
        return new ResponseEntity<>(validationError, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle general exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        ApiError apiError = ApiError.builder()
                .message("服务器内部错误: " + ex.getMessage())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(ZonedDateTime.now())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 