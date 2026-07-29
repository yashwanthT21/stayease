package com.stayease.stay.dto;

import com.stayease.stay.enums.CheckOutStatus;

import java.time.LocalDateTime;

public record CheckOutRecordResponse(
        Long id,
        Long reservationId,
        LocalDateTime actualCheckOut,
        boolean damageNoted,
        String damageDescription,
        boolean depositReleased,
        CheckOutStatus status
) {
}
