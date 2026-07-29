package com.stayease.common.exception;

/**
 * Thrown when we look up something by id (or another key) and it isn't there.
 * The GlobalExceptionHandler turns this into an HTTP 404 (Not Found) response.
 *
 * RuntimeException is "unchecked" — we don't have to declare it with `throws`
 * on every method, which keeps the service code clean.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
        System.out.println("user not found!");
    }
}