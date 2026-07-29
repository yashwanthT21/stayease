package com.stayease.iam.auth.dto;

import com.stayease.iam.enums.UserRole;

/**
 * Returned by register & login. The client stores `token` and sends it on
 * subsequent requests as: Authorization: Bearer <token>
 */
public record AuthResponse(
        String token,
        String tokenType, // always "Bearer"
        Long userId,
        String email,
        UserRole role
) {
}
