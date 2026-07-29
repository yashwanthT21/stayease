package com.stayease.booking.dto;

import com.stayease.booking.enums.GuestStatus;
import com.stayease.booking.enums.VerificationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Incoming JSON for a guest profile.
 *
 * Notice what's NOT here: reviewScore and bookingCount. Those are SERVER-MANAGED
 * (review score comes from reviews; booking count is incremented when bookings
 * are made). Leaving them out of the request means a client can never fake them.
 */
public record GuestProfileRequest(

        @NotNull(message = "userId is required")
        Long userId,

        @NotBlank(message = "name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 180)
        String email,

        @Size(max = 20)
        String phone,

        @Size(max = 80)
        String nationality,

        VerificationStatus verificationStatus, // optional — defaults to UNVERIFIED

        GuestStatus status                      // optional — defaults to ACTIVE
) {
}
