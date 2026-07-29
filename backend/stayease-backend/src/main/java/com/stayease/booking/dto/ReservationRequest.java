package com.stayease.booking.dto;

import com.stayease.booking.enums.BookingSource;
import com.stayease.booking.enums.ReservationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Incoming JSON for a reservation.
 *
 * IMPORTANT — what's missing on purpose:
 *  - nights      : the server computes it from checkInDate/checkOutDate.
 *  - totalAmount : the server computes it = baseAmount + cleaningFee + serviceFee.
 *
 * The client only states the facts (dates, fees); the server owns the maths so
 * the totals can always be trusted.
 */
public record ReservationRequest(

        @NotNull(message = "propertyId is required")
        Long propertyId,

        @NotNull(message = "guestId is required")
        Long guestId,

        @NotNull(message = "checkInDate is required")
        LocalDate checkInDate,

        @NotNull(message = "checkOutDate is required")
        LocalDate checkOutDate,

        @NotNull(message = "guestCount is required")
        @Min(value = 1, message = "guestCount must be at least 1")
        Integer guestCount,

        @NotNull(message = "baseAmount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "baseAmount must be greater than 0")
        BigDecimal baseAmount,

        @DecimalMin(value = "0.0", message = "cleaningFee cannot be negative")
        BigDecimal cleaningFee, // optional

        @DecimalMin(value = "0.0", message = "serviceFee cannot be negative")
        BigDecimal serviceFee,  // optional

        BookingSource bookingSource, // optional — defaults to DIRECT

        ReservationStatus status     // optional — defaults to PENDING
) {
}
