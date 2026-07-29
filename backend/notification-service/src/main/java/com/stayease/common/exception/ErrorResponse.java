package com.stayease.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The consistent JSON shape returned for every error, so clients always get a
 * predictable body instead of a raw stack trace. @JsonInclude(NON_NULL) omits
 * null fields — so `fieldErrors` only appears for validation failures.
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
