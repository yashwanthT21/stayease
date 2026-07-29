package com.stayease.stay.dto;

import com.stayease.stay.enums.ReviewStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GuestReviewResponse(
        Long id,
        Long reservationId,
        Long guestId,
        Integer cleanlinessScore,
        Integer accuracyScore,
        Integer locationScore,
        Integer valueScore,
        BigDecimal overallScore,
        String comments,
        LocalDateTime submittedDate,
        ReviewStatus status
) {
}
