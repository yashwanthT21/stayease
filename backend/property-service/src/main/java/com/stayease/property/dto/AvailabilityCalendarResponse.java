package com.stayease.property.dto;

import com.stayease.property.enums.AvailabilityStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Outgoing JSON for an availability row. lastUpdated is stamped by the server.
 */
public record AvailabilityCalendarResponse(
        Long id,
        Long propertyId,
        LocalDate calendarDate,
        AvailabilityStatus availabilityStatus,
        BigDecimal basePrice,
        int minimumNights,
        LocalDateTime lastUpdated
) {
}
