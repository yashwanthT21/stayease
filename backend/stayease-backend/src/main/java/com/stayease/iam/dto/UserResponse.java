package com.stayease.iam.dto;

import com.stayease.iam.enums.UserRole;
import com.stayease.iam.enums.UserStatus;

/**
 * The shape of the JSON we SEND BACK to the client.
 *
 * Why a separate response type instead of returning the User entity directly?
 *  - Security: you control exactly which fields leave the app (later, when User
 *    gains a password hash, it must never appear in a response — a DTO makes
 *    that impossible by construction).
 *  - Decoupling: the API contract can stay stable even if the entity changes.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        UserRole role,
        UserStatus status
) {
}
