package com.stayease.booking.mapper;

import com.stayease.booking.dto.ReservationRequest;
import com.stayease.booking.dto.ReservationResponse;
import com.stayease.booking.entity.Reservation;
import com.stayease.booking.enums.BookingSource;
import com.stayease.booking.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;

/**
 * Entity ⇆ DTO conversion for Reservation, including the server-side maths.
 *
 *  nights      = number of days between check-in and check-out
 *  totalAmount = baseAmount + cleaningFee + serviceFee  (missing fees count as 0)
 *
 * The date order (checkOut after checkIn) is validated in the service BEFORE
 * we get here, so nights is always >= 1.
 */
public final class ReservationMapper {

    private ReservationMapper() {
    }

    public static Reservation toEntity(ReservationRequest request) {
        Reservation r = new Reservation();
        applyRequest(r, request);
        r.setBookingSource(request.bookingSource() != null
                ? request.bookingSource()
                : BookingSource.DIRECT);
        r.setStatus(request.status() != null ? request.status() : ReservationStatus.PENDING);
        return r;
    }

    public static void updateEntity(Reservation r, ReservationRequest request) {
        applyRequest(r, request);
        if (request.bookingSource() != null) {
            r.setBookingSource(request.bookingSource());
        }
        if (request.status() != null) {
            r.setStatus(request.status());
        }
    }

    /** Copies client-supplied fields AND recomputes the server-owned ones. */
    private static void applyRequest(Reservation r, ReservationRequest request) {
        r.setPropertyId(request.propertyId());
        r.setGuestId(request.guestId());
        r.setCheckInDate(request.checkInDate());
        r.setCheckOutDate(request.checkOutDate());
        r.setGuestCount(request.guestCount());
        r.setBaseAmount(request.baseAmount());
        r.setCleaningFee(request.cleaningFee());
        r.setServiceFee(request.serviceFee());

        // --- server-computed fields ---
        int nights = (int) ChronoUnit.DAYS.between(request.checkInDate(), request.checkOutDate());
        r.setNights(nights);

        BigDecimal total = request.baseAmount()
                .add(orZero(request.cleaningFee()))
                .add(orZero(request.serviceFee()));
        r.setTotalAmount(total);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public static ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getPropertyId(),
                r.getGuestId(),
                r.getCheckInDate(),
                r.getCheckOutDate(),
                r.getNights(),
                r.getGuestCount(),
                r.getBaseAmount(),
                r.getCleaningFee(),
                r.getServiceFee(),
                r.getTotalAmount(),
                r.getBookingSource(),
                r.getStatus());
    }
}
