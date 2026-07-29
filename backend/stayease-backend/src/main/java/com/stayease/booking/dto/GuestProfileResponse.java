package com.stayease.booking.dto;

import com.stayease.booking.enums.GuestStatus;
import com.stayease.booking.enums.VerificationStatus;

import java.math.BigDecimal;

public record GuestProfileResponse(
        Long id,
        Long userId,
        String name,
        String email,
        String phone,
        String nationality,
        VerificationStatus verificationStatus,
        BigDecimal reviewScore,
        int bookingCount,
        GuestStatus status
) {
}
