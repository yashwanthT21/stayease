package com.stayease.iam.dto;

import com.stayease.iam.enums.UserRole;

/**
 * The bare minimum needed to NAME a user: who they are and what they do.
 *
 * Deliberately narrower than {@link UserResponse} — no email, phone or account
 * status. Other services (property-service) call this to put "assigned by Ada
 * Owner" in a notification instead of "assigned by user #5", and that use case
 * needs nothing more. Keeping it a separate shape is what lets the endpoint be
 * readable by any signed-in caller without widening what a non-admin can see
 * about someone else.
 */
public record UserSummaryResponse(
        Long id,
        String name,
        UserRole role
) {
}
