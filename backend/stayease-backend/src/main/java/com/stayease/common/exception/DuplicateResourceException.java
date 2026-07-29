package com.stayease.common.exception;

/**
 * Thrown when a uniqueness rule would be violated — e.g. registering a User
 * with an email that already exists. The GlobalExceptionHandler turns this
 * into an HTTP 409 (Conflict) response.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
