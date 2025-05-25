package com.huang.backend.exception;

/**
 * Exception thrown when a requested resource cannot be found.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Constructs a ResourceNotFoundException with a default message
     */
    public ResourceNotFoundException() {
        super("Resource not found");
    }

    /**
     * Constructs a ResourceNotFoundException with a specific message
     * 
     * @param message the detail message
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    /**
     * Constructs a ResourceNotFoundException for a specific resource with a given ID
     * 
     * @param resourceName the name of the resource
     * @param fieldName the name of the ID field
     * @param fieldValue the value of the ID field
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
} 