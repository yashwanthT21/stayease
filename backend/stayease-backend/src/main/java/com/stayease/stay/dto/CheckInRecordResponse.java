package com.stayease.stay.dto;

import com.stayease.stay.enums.AccessMethod;
import com.stayease.stay.enums.CheckInStatus;

import java.time.LocalDateTime;

public record CheckInRecordResponse(
        Long id,
        Long reservationId,
        Long guestId,
        LocalDateTime actualCheckIn,
        AccessMethod accessMethod,
        boolean welcomePackSent,
        CheckInStatus status
) {
}
