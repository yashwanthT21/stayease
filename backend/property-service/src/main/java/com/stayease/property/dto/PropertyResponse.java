package com.stayease.property.dto;

import com.stayease.property.enums.PropertyStatus;
import com.stayease.property.enums.PropertyType;

import java.time.LocalTime;

/**
 * Outgoing JSON for a property. Mirrors the columns the client is allowed to see.
 */
public record PropertyResponse(
        Long id,
        Long ownerId,
        Long managerId,
        String title,
        PropertyType type,
        String city,
        int maxGuests,
        int bedrooms,
        int bathrooms,
        String amenitiesList,
        String houseRules,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        PropertyStatus status
) {
}
