package com.stayease.iam.auth.dto;

import com.stayease.iam.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sign-up payload. The raw password arrives here, is immediately BCrypt-hashed
 * by the service, and is never stored or returned in plain text.
 *
 * (Allowing the client to choose its own role is a learning-project
 * simplification; a real system would provision privileged roles separately.)
 */
public record RegisterRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 180)
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 6, max = 72, message = "password must be 6-72 characters")
        String password,

        @Size(max = 20)
        String phone,

        @NotNull(message = "role is required")
        UserRole role
) {
}
