package com.stayease.iam.dto;

import com.stayease.iam.enums.UserRole;
import com.stayease.iam.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The shape of the JSON a client SENDS when creating or updating a user.
 *
 * The annotations are validation rules checked automatically when the
 * controller marks this parameter with @Valid. If any rule fails, Spring
 * throws MethodArgumentNotValidException, which our GlobalExceptionHandler
 * turns into a 400 response listing the bad fields.
 *
 * Notice there is NO `id` here — the database assigns the id, the client never
 * sends one. `status` is optional; the service defaults it to ACTIVE.
 */
public record UserRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 180)
        String email,

        @Size(max = 20, message = "phone must be at most 20 characters")
        String phone,

        @NotNull(message = "role is required")
        UserRole role,

        UserStatus status
) {
}
