package com.stayease.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The consistent JSON shape returned for every error, so the frontend (and you
 * in Postman) always get a predictable body instead of a raw stack trace.
 *
 * This is a Java "record": a compact, immutable data carrier. The compiler
 * generates the constructor, getters, equals(), hashCode() and toString().
 *
 * @JsonInclude(NON_NULL) tells Jackson (the JSON library) to omit fields that
 * are null — so `fieldErrors` only appears for validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
