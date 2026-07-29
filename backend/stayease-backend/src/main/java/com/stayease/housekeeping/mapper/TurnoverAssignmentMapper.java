package com.stayease.housekeeping.mapper;

import com.stayease.housekeeping.dto.TurnoverAssignmentRequest;
import com.stayease.housekeeping.dto.TurnoverAssignmentResponse;
import com.stayease.housekeeping.entity.TurnoverAssignment;
import com.stayease.housekeeping.enums.TurnoverStatus;

public final class TurnoverAssignmentMapper {

    private TurnoverAssignmentMapper() {
    }

    public static TurnoverAssignment toEntity(TurnoverAssignmentRequest request) {
        TurnoverAssignment t = new TurnoverAssignment();
        t.setPropertyId(request.propertyId());
        apply(t, request);
        return t;
    }

    public static void updateEntity(TurnoverAssignment t, TurnoverAssignmentRequest request) {
        t.setPropertyId(request.propertyId());
        apply(t, request);
    }

    private static void apply(TurnoverAssignment t, TurnoverAssignmentRequest request) {
        t.setCheckOutReservationId(request.checkOutReservationId());
        t.setCheckInReservationId(request.checkInReservationId());
        t.setAssignedToId(request.assignedToId());
        t.setAssignedDate(request.assignedDate());
        t.setStartByTime(request.startByTime());
        t.setCompleteByTime(request.completeByTime());
        t.setStatus(request.status() != null ? request.status() : TurnoverStatus.PENDING);
    }

    public static TurnoverAssignmentResponse toResponse(TurnoverAssignment t) {
        return new TurnoverAssignmentResponse(
                t.getId(),
                t.getPropertyId(),
                t.getCheckOutReservationId(),
                t.getCheckInReservationId(),
                t.getAssignedToId(),
                t.getAssignedDate(),
                t.getStartByTime(),
                t.getCompleteByTime(),
                t.getStatus());
    }
}
