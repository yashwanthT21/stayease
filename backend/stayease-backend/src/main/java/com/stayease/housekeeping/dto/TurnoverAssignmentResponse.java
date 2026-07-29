package com.stayease.housekeeping.dto;

import com.stayease.housekeeping.enums.TurnoverStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TurnoverAssignmentResponse(
        Long id,
        Long propertyId,
        Long checkOutReservationId,
        Long checkInReservationId,
        Long assignedToId,
        LocalDate assignedDate,
        LocalDateTime startByTime,
        LocalDateTime completeByTime,
        TurnoverStatus status
) {
}
