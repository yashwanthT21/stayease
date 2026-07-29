package com.stayease.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a clean 403 JSON body when an authenticated user lacks the required
 * role for an endpoint (e.g. a GUEST calling an ADMIN-only route).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        RestAuthenticationEntryPoint.writeJsonError(response, HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource", request.getRequestURI());
    }
}
