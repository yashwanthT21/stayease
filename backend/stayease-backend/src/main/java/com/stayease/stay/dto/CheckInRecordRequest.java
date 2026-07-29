package com.stayease.stay.dto;

import com.stayease.stay.enums.AccessMethod;
import com.stayease.stay.enums.CheckInStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CheckInRecordRequest(

        @NotNull(message = "reservationId is required")
        Long reservationId,

        @NotNull(message = "guestId is required")
        Long guestId,

        LocalDateTime actualCheckIn,   // optional

        AccessMethod accessMethod,     // optional

        Boolean welcomePackSent,       // optional — defaults to false

        CheckInStatus status           // optional — defaults to PENDING
) {
}
