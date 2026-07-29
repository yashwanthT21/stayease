package com.stayease.booking.dto;

import com.stayease.booking.enums.BookingSource;
import com.stayease.booking.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outgoing JSON for a reservation. nights and totalAmount are the
 * server-computed fields the client could not set.
 */
public record ReservationResponse(
        Long id,
        Long propertyId,
        Long guestId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int nights,
        int guestCount,
        BigDecimal baseAmount,
        BigDecimal cleaningFee,
        BigDecimal serviceFee,
        BigDecimal totalAmount,
        BookingSource bookingSource,
        ReservationStatus status
) {
}
