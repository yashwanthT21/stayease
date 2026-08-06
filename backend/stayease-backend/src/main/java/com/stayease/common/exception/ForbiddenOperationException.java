package com.stayease.common.exception;

/**
 * The caller is authenticated but not allowed to do THIS, to THIS record.
 *
 * URL-level rules in SecurityConfig can only say "an OWNER may approve a
 * statement"; they can't say "…but only their own". That second half has to be
 * decided where the record is loaded, so the service throws this and it maps to a
 * 403 (see GlobalExceptionHandler).
 *
 * Deliberately not Spring Security's AccessDeniedException: keeping the service
 * layer free of security-framework types means it can be unit-tested without one.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
