package com.stayease.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * One place that catches exceptions thrown ANYWHERE in the app and converts
 * them into clean JSON responses with the right HTTP status code.
 *
 * @RestControllerAdvice = "advice" (cross-cutting behaviour) that applies to
 * every @RestController. Each @ExceptionHandler method below handles one type
 * of exception. Without this class, an unhandled exception would return an
 * ugly 500 error with a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404 — something was looked up by id and not found. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /** 409 — a uniqueness rule was violated (e.g. duplicate email). */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * 400 — the incoming JSON failed @Valid validation (e.g. blank name,
     * invalid email). Spring throws MethodArgumentNotValidException, and we
     * collect every field error into a map: { "email": "must be a valid email" }.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI(),
                fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 401 — login failed (wrong email/password). BadCredentialsException is an
     * AuthenticationException thrown by the AuthenticationManager DURING the
     * login controller call, so unlike token-filter failures it reaches here.
     */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    /**
     * 403 — authenticated, but not allowed to touch THIS record (e.g. an owner
     * approving someone else's statement). SecurityConfig's URL rules can't express
     * per-record ownership, so the service decides it and we map it here; without
     * this handler the catch-all below would report it as a 500.
     */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            ForbiddenOperationException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /**
     * 400 — a business rule rejected the input (e.g. endDate before startDate).
     * Unlike @Valid failures, these are decided in the service, so we throw a
     * plain IllegalArgumentException and map it here.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * 405 — the URL exists but not for this HTTP verb (e.g. POST to a read-only
     * endpoint). Without this specific handler, the catch-all below would wrongly
     * turn Spring's 405 into a 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint", request);
    }

    /**
     * 400 — the request body couldn't be parsed (malformed JSON, wrong type for
     * a field, etc.). We keep the message generic so we don't leak parser internals.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", request);
    }

    /**
     * 400 — a path/query parameter couldn't be converted to its target type,
     * e.g. ?status=NONSENSE for an enum, or a non-numeric id in the URL.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'", request);
    }

    /**
     * 500 — a safety net for anything we didn't anticipate. We avoid leaking
     * the raw exception details to the client and just log-friendly message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong: " + ex.getMessage(), request);
    }

    /** Shared helper that assembles the ErrorResponse + ResponseEntity. */
    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                null);
        return ResponseEntity.status(status).body(body);
    }
}
