package com.stayease.stay.mapper;

import com.stayease.stay.dto.CheckInRecordRequest;
import com.stayease.stay.dto.CheckInRecordResponse;
import com.stayease.stay.entity.CheckInRecord;
import com.stayease.stay.enums.CheckInStatus;

public final class CheckInRecordMapper {

    private CheckInRecordMapper() {
    }

    public static CheckInRecord toEntity(CheckInRecordRequest request) {
        CheckInRecord c = new CheckInRecord();
        c.setReservationId(request.reservationId());
        c.setGuestId(request.guestId());
        apply(c, request);
        return c;
    }

    public static void updateEntity(CheckInRecord c, CheckInRecordRequest request) {

        apply(c, request);
    }

    private static void apply(CheckInRecord c, CheckInRecordRequest request) {
        c.setActualCheckIn(request.actualCheckIn());
        c.setAccessMethod(request.accessMethod());
        c.setWelcomePackSent(Boolean.TRUE.equals(request.welcomePackSent()));
        c.setStatus(request.status() != null ? request.status() : CheckInStatus.PENDING);
    }

    public static CheckInRecordResponse toResponse(CheckInRecord c) {
        return new CheckInRecordResponse(
                c.getId(),
                c.getReservationId(),
                c.getGuestId(),
                c.getActualCheckIn(),
                c.getAccessMethod(),
                c.isWelcomePackSent(),
                c.getStatus());
    }
}
