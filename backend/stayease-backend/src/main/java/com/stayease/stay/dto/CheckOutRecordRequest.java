package com.stayease.stay.dto;

import com.stayease.stay.enums.CheckOutStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CheckOutRecordRequest(

        @NotNull(message = "reservationId is required")
        Long reservationId,

        LocalDateTime actualCheckOut,  // optional

        Boolean damageNoted,           // optional — defaults to false

        String damageDescription,      // optional

        Boolean depositReleased,       // optional — defaults to false

        CheckOutStatus status          // optional — defaults to CHECKED_OUT
) {
}
