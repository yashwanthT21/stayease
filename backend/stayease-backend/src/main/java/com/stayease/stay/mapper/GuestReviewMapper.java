package com.stayease.stay.mapper;

import com.stayease.stay.dto.GuestReviewRequest;
import com.stayease.stay.dto.GuestReviewResponse;
import com.stayease.stay.entity.GuestReview;
import com.stayease.stay.enums.ReviewStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Stream;

public final class GuestReviewMapper {

    private GuestReviewMapper() {
    }

    public static GuestReview toEntity(GuestReviewRequest request) {
        GuestReview g = new GuestReview();
        g.setReservationId(request.reservationId());
        g.setGuestId(request.guestId());
        apply(g, request);
        g.setSubmittedDate(LocalDateTime.now()); // stamped once, at creation
        return g;
    }

    public static void updateEntity(GuestReview g, GuestReviewRequest request) {
        apply(g, request); // recomputes overallScore; submittedDate left unchanged
    }

    private static void apply(GuestReview g, GuestReviewRequest request) {
        g.setCleanlinessScore(request.cleanlinessScore());
        g.setAccuracyScore(request.accuracyScore());
        g.setLocationScore(request.locationScore());
        g.setValueScore(request.valueScore());
        g.setComments(request.comments());
        g.setStatus(request.status() != null ? request.status() : ReviewStatus.PUBLISHED);
        g.setOverallScore(computeOverall(request)); // server-computed
    }

    /** Average of whichever of the four scores were provided; null if none. */
    private static BigDecimal computeOverall(GuestReviewRequest request) {
        var scores = Stream.of(
                        request.cleanlinessScore(),
                        request.accuracyScore(),
                        request.locationScore(),
                        request.valueScore())
                .filter(Objects::nonNull) //no null
                .toList();
        if (scores.isEmpty()) {
            return null;
        }
        double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    public static GuestReviewResponse toResponse(GuestReview g) {
        return new GuestReviewResponse(
                g.getId(),
                g.getReservationId(),
                g.getGuestId(),
                g.getCleanlinessScore(),
                g.getAccuracyScore(),
                g.getLocationScore(),
                g.getValueScore(),
                g.getOverallScore(),
                g.getComments(),
                g.getSubmittedDate(),
                g.getStatus());
    }
}
