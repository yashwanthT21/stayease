package com.stayease.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the gateway's JWT verification.
 *
 * The gateway never MINTS tokens — IAM does that at login. It only verifies them,
 * using the same HS256 secret, which is what lets it reject a bad request at the
 * edge without any call back to IAM. That's the whole argument for a stateless
 * token, so these tests cover the three cases that matter: a good token parses, a
 * token signed with a different secret is refused, and an expired token is refused
 * even though its signature is perfectly valid.
 *
 * No Spring context here — JwtService takes the secret as a constructor argument,
 * so it can be built directly.
 */
class JwtServiceTest {

    private static final String SECRET = "stayease-dev-secret-key-change-me-please-0123456789abcdef";
    private static final String OTHER_SECRET = "an-entirely-different-secret-key-0123456789abcdefghij";

    private final JwtService jwtService = new JwtService(SECRET);

    /** Mints a token the way IAM does, so the test exercises the real format. */
    private String token(String secret, String email, String role, long expiresInMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("userId", 7L)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiresInMs))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("parse: a token signed with the shared secret yields its claims")
    void parseReturnsClaimsForAValidToken() {
        String valid = token(SECRET, "manager@example.com", "PROPERTY_MANAGER", 60_000);

        Claims claims = jwtService.parse(valid);

        assertThat(claims.getSubject()).isEqualTo("manager@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("PROPERTY_MANAGER");
        assertThat(claims.get("userId", Integer.class)).isEqualTo(7);
    }

    @Test
    @DisplayName("parse: a token signed with a DIFFERENT secret is refused")
    void parseRejectsAForgedSignature() {
        String forged = token(OTHER_SECRET, "attacker@example.com", "ADMIN", 60_000);

        // The filter turns this into a 401 — the request never reaches a service.
        assertThatThrownBy(() -> jwtService.parse(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parse: an expired token is refused even though its signature is valid")
    void parseRejectsAnExpiredToken() {
        // Issued and expired in the past — correctly signed, but no longer usable.
        String expired = token(SECRET, "manager@example.com", "PROPERTY_MANAGER", -1_000);

        assertThatThrownBy(() -> jwtService.parse(expired))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parse: a garbage string is refused rather than crashing oddly")
    void parseRejectsGarbage() {
        assertThatThrownBy(() -> jwtService.parse("not-a-jwt-at-all"))
                .isInstanceOf(JwtException.class);
    }
}
