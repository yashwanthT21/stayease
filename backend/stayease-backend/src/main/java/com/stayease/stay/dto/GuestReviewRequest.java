package com.stayease.stay.dto;

import com.stayease.stay.enums.ReviewStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Incoming JSON for a guest review.
 *
 * Server-managed (NOT here): overallScore (computed = average of the four
 * scores) and submittedDate (stamped when the review is created).
 *
 * Each score is optional but, when present, must be 1..5.
 */
public record GuestReviewRequest(

        @NotNull(message = "reservationId is required")
        Long reservationId,

        @NotNull(message = "guestId is required")
        Long guestId,

        @Min(1) @Max(5)
        Integer cleanlinessScore,

        @Min(1) @Max(5)
        Integer accuracyScore,

        @Min(1) @Max(5)
        Integer locationScore,

        @Min(1) @Max(5)
        Integer valueScore,

        String comments,

        ReviewStatus status // optional — defaults to PUBLISHED
) {
}
