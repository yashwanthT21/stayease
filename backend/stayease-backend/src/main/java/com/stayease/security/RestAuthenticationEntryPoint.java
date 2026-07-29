package com.stayease.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Returns a clean 401 JSON body when an unauthenticated request hits a
 * protected endpoint. Security runs in the servlet filter chain, BEFORE Spring
 * MVC, so our @RestControllerAdvice never sees these — hence this handler.
 *
 * We hand-build the small JSON string rather than inject an ObjectMapper, which
 * keeps these two handlers free of any Jackson-version coupling.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeJsonError(response, HttpStatus.UNAUTHORIZED,
                "Authentication required: missing or invalid token", request.getRequestURI());
    }

    /** Shared by the 401 entry point and the 403 access-denied handler. */
    static void writeJsonError(HttpServletResponse response, HttpStatus status,
                               String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String json = "{"
                + "\"timestamp\":\"" + LocalDateTime.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + status.getReasonPhrase() + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"path\":\"" + escape(path) + "\""
                + "}";
        response.getWriter().write(json);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
