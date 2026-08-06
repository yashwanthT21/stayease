package com.stayease.property.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Cross-cutting configuration for every outbound Feign call from this service.
 *
 * Identity propagation: property-service itself is unauthenticated (the gateway
 * checks the JWT at the edge), but IAM is not — a call to /api/users/... needs a
 * token. Rather than mint a service credential, we forward the ORIGINAL caller's
 * Authorization header, so the downstream call carries exactly the permissions
 * the human already had. Nothing is added when there is no header to copy (a
 * background thread, or a direct call that bypassed the gateway); the callers all
 * degrade gracefully when the lookup fails.
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor authorizationForwardingInterceptor() {
        return template -> {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
                return; // not serving an HTTP request — nothing to forward
            }
            String authorization = servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()
                    && !template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        };
    }
}
