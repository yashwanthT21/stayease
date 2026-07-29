package com.stayease.common.exception;

/**
 * Thrown when a uniqueness rule would be violated (e.g. two availability rows for
 * the same property + date). The GlobalExceptionHandler turns this into an
 * HTTP 409 (Conflict) response.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
