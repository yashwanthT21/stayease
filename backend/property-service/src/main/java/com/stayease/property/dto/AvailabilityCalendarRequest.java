package com.stayease.property.dto;

import com.stayease.property.enums.AvailabilityStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Incoming JSON for a single calendar day's availability + price.
 *
 * basePrice uses BigDecimal (never double for money). @DecimalMin keeps it from
 * being zero/negative. calendarDate is a "yyyy-MM-dd" string in JSON.
 */
public record AvailabilityCalendarRequest(

        @NotNull(message = "propertyId is required")
        Long propertyId,

        @NotNull(message = "calendarDate is required")
        LocalDate calendarDate,

        AvailabilityStatus availabilityStatus, // optional — defaults to AVAILABLE

        @NotNull(message = "basePrice is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "basePrice must be greater than 0")
        BigDecimal basePrice,

        @NotNull(message = "minimumNights is required")
        @Min(value = 1, message = "minimumNights must be at least 1")
        Integer minimumNights
) {
}
