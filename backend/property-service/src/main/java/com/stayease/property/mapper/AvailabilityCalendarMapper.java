package com.stayease.property.mapper;

import com.stayease.property.dto.AvailabilityCalendarRequest;
import com.stayease.property.dto.AvailabilityCalendarResponse;
import com.stayease.property.entity.AvailabilityCalendar;
import com.stayease.property.enums.AvailabilityStatus;

import java.time.LocalDateTime;

/**
 * Entity ⇆ DTO conversion for AvailabilityCalendar.
 *
 * We stamp lastUpdated = now() whenever the row is written, so callers always
 * see when the price/availability was last touched. propertyId is set only on
 * create — an availability row cannot be moved to a different property on update.
 */
public final class AvailabilityCalendarMapper {

    private AvailabilityCalendarMapper() {
    }

    public static AvailabilityCalendar toEntity(AvailabilityCalendarRequest request) {
        AvailabilityCalendar a = new AvailabilityCalendar();
        a.setPropertyId(request.propertyId());
        a.setCalendarDate(request.calendarDate());
        a.setAvailabilityStatus(request.availabilityStatus() != null
                ? request.availabilityStatus()
                : AvailabilityStatus.AVAILABLE);
        a.setBasePrice(request.basePrice());
        a.setMinimumNights(request.minimumNights());
        a.setLastUpdated(LocalDateTime.now());
        return a;
    }

    public static void updateEntity(AvailabilityCalendar a, AvailabilityCalendarRequest request) {
        a.setCalendarDate(request.calendarDate());
        if (request.availabilityStatus() != null) {
            a.setAvailabilityStatus(request.availabilityStatus());
        }
        a.setBasePrice(request.basePrice());
        a.setMinimumNights(request.minimumNights());
        a.setLastUpdated(LocalDateTime.now());
    }

    public static AvailabilityCalendarResponse toResponse(AvailabilityCalendar a) {
        return new AvailabilityCalendarResponse(
                a.getId(),
                a.getPropertyId(),
                a.getCalendarDate(),
                a.getAvailabilityStatus(),
                a.getBasePrice(),
                a.getMinimumNights(),
                a.getLastUpdated());
    }
}
