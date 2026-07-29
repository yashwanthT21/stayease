package com.stayease.booking.mapper;

import com.stayease.booking.dto.GuestProfileRequest;
import com.stayease.booking.dto.GuestProfileResponse;
import com.stayease.booking.entity.GuestProfile;
import com.stayease.booking.enums.GuestStatus;
import com.stayease.booking.enums.VerificationStatus;

public final class GuestProfileMapper {

    private GuestProfileMapper() {
    }

    public static GuestProfile toEntity(GuestProfileRequest request) {
        GuestProfile g = new GuestProfile();
        g.setUserId(request.userId());
        g.setName(request.name());
        g.setEmail(request.email());
        g.setPhone(request.phone());
        g.setNationality(request.nationality());
        g.setVerificationStatus(request.verificationStatus() != null
                ? request.verificationStatus()
                : VerificationStatus.UNVERIFIED);
        g.setStatus(request.status() != null ? request.status() : GuestStatus.ACTIVE);
        // Server-managed starting values — never taken from the client.
        g.setBookingCount(0);
        // reviewScore left null until the guest receives reviews.
        return g;
    }

    /**
     * Update editable fields only. We deliberately do NOT touch bookingCount or
     * reviewScore here — those are managed by the system, not this request.
     */
    public static void updateEntity(GuestProfile g, GuestProfileRequest request) {
        g.setName(request.name());
        g.setEmail(request.email());
        g.setPhone(request.phone());
        g.setNationality(request.nationality());
        if (request.verificationStatus() != null) {
            g.setVerificationStatus(request.verificationStatus());
        }
        if (request.status() != null) {
            g.setStatus(request.status());
        }
    }

    public static GuestProfileResponse toResponse(GuestProfile g) {
        return new GuestProfileResponse(
                g.getId(),
                g.getUserId(),
                g.getName(),
                g.getEmail(),
                g.getPhone(),
                g.getNationality(),
                g.getVerificationStatus(),
                g.getReviewScore(),
                g.getBookingCount(),
                g.getStatus());
    }
}
