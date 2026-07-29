package com.stayease.gateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Edge authentication. Runs for EVERY request reaching the gateway:
 *
 *  - Public paths (login/register, docs, health) pass straight through.
 *  - Any other request must carry a valid "Authorization: Bearer &lt;jwt&gt;".
 *    We verify it once here so the downstream services don't each re-implement
 *    auth. On success we ADD trusted identity headers (X-User-Id / X-User-Role /
 *    X-User-Email) that services can rely on because they only accept traffic
 *    from behind this gateway.
 *  - Missing/invalid/expired token -> 401 with a small JSON body.
 *
 * Implemented as a core Spring {@link WebFilter} (not a gateway GlobalFilter) so
 * it sits on the stable Spring Framework API and mutates the request before the
 * gateway's routing handler runs.
 */
@Component
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    /** Prefixes that don't require a token. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth",        // login & register (proxied to IAM)
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui");

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtService.parse(token);

            // Forward the verified identity so services never parse the token
            // themselves. Setting these (rather than adding) also means any
            // client-supplied X-User-* headers are overwritten, so they can't be
            // spoofed from outside the trust boundary.
            ServerHttpRequest mutated = request.mutate()
                    .headers(h -> {
                        h.set("X-User-Id", String.valueOf(claims.get("userId")));
                        h.set("X-User-Role", String.valueOf(claims.get("role")));
                        h.set("X-User-Email", claims.getSubject());
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception ex) {
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\""
                + message + "\"}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /** Run early, before the gateway routes the request. */
    @Override
    public int getOrder() {
        return -1;
    }
}
