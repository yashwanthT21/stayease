package com.stayease.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Verifies JWTs at the gateway.
 *
 * The gateway does not MINT tokens (IAM does that on login); it only VERIFIES
 * them. Because it shares the same HS256 secret as the IAM module, it can check
 * the signature and expiry without any call back to IAM — the whole point of a
 * stateless token. A tampered, expired, or wrongly-signed token throws while
 * parsing, which the filter turns into a 401.
 */
@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Parse + verify signature and expiry. Throws if the token is not valid. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
