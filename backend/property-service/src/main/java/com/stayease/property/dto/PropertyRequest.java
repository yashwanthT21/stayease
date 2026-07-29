package com.stayease.property.dto;

import com.stayease.property.enums.PropertyStatus;
import com.stayease.property.enums.PropertyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * Incoming JSON for creating/updating a property.
 *
 * ownerId/managerId are user ids. Annotations here cover the cheap, format-level
 * rules only. checkInTime/checkOutTime accept JSON strings like "14:00".
 */
public record PropertyRequest(

        @NotNull(message = "ownerId is required")
        Long ownerId,

        Long managerId, // optional — a property may not have a manager yet

        @NotBlank(message = "title is required")
        @Size(max = 200)
        String title,

        @NotNull(message = "type is required")
        PropertyType type,

        @NotBlank(message = "city is required")
        @Size(max = 120)
        String city,

        @NotNull(message = "maxGuests is required")
        @Min(value = 1, message = "maxGuests must be at least 1")
        Integer maxGuests,

        @NotNull(message = "bedrooms is required")
        @Min(value = 0, message = "bedrooms cannot be negative")
        Integer bedrooms,

        @NotNull(message = "bathrooms is required")
        @Min(value = 0, message = "bathrooms cannot be negative")
        Integer bathrooms,

        String amenitiesList, // free text (stored as TEXT)

        String houseRules,    // free text (stored as TEXT)

        LocalTime checkInTime,

        LocalTime checkOutTime,

        PropertyStatus status // optional — service defaults to UNLISTED
) {
}
