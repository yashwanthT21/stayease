package com.stayease.property.mapper;

import com.stayease.property.dto.PropertyRequest;
import com.stayease.property.dto.PropertyResponse;
import com.stayease.property.entity.Property;
import com.stayease.property.enums.PropertyStatus;

/**
 * Entity ⇆ DTO conversion for Property.
 */
public final class PropertyMapper {

    private PropertyMapper() {
    }

    public static Property toEntity(PropertyRequest request) {
        Property p = new Property();
        applyRequest(p, request);
        // status defaults to UNLISTED for a brand-new listing
        p.setStatus(request.status() != null ? request.status() : PropertyStatus.UNLISTED);
        return p;
    }

    public static void updateEntity(Property p, PropertyRequest request) {
        applyRequest(p, request);
        if (request.status() != null) {
            p.setStatus(request.status());
        }
    }

    /** Shared field-copy used by both create and update. */
    private static void applyRequest(Property p, PropertyRequest request) {
        p.setOwnerId(request.ownerId());
        p.setManagerId(request.managerId());
        p.setTitle(request.title());
        p.setType(request.type());
        p.setCity(request.city());
        p.setMaxGuests(request.maxGuests());
        p.setBedrooms(request.bedrooms());
        p.setBathrooms(request.bathrooms());
        p.setAmenitiesList(request.amenitiesList());
        p.setHouseRules(request.houseRules());
        p.setCheckInTime(request.checkInTime());
        p.setCheckOutTime(request.checkOutTime());
    }

    public static PropertyResponse toResponse(Property p) {
        return new PropertyResponse(
                p.getId(),
                p.getOwnerId(),
                p.getManagerId(),
                p.getTitle(),
                p.getType(),
                p.getCity(),
                p.getMaxGuests(),
                p.getBedrooms(),
                p.getBathrooms(),
                p.getAmenitiesList(),
                p.getHouseRules(),
                p.getCheckInTime(),
                p.getCheckOutTime(),
                p.getStatus());
    }
}
