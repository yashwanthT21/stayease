package com.stayease.housekeeping.dto;

import com.stayease.housekeeping.enums.TurnoverStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TurnoverAssignmentRequest(

        @NotNull(message = "propertyId is required")
        Long propertyId,

        Long checkOutReservationId, // optional — the stay being cleaned up after

        Long checkInReservationId,  // optional — the next stay being prepared for

        Long assignedToId,          // optional — housekeeping user

        LocalDate assignedDate,

        LocalDateTime startByTime,

        LocalDateTime completeByTime,

        TurnoverStatus status       // optional — defaults to PENDING
) {
}
