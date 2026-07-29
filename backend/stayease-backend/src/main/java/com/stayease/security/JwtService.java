package com.stayease.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Creates and validates JWTs (JSON Web Tokens).
 *
 * A JWT has three parts: header.payload.signature. The payload carries
 * "claims" (subject = the user's email, plus role and userId). The signature is
 * an HMAC computed with our secret key — so the server can later verify the
 * token wasn't tampered with, WITHOUT storing any session server-side.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // HS256 needs a key of at least 256 bits (32 bytes); our secret is longer.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Build a signed token for a user, embedding extra claims (role, userId). */
    public String generateToken(UserDetails user, Map<String, Object> extraClaims) {
        Date now = new Date();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(user.getUsername())          // the email
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Token is valid if it parses, matches the user, and hasn't expired. */
    public boolean isTokenValid(String token, UserDetails user) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject().equals(user.getUsername())
                    && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false; // bad signature, malformed, expired, etc.
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
