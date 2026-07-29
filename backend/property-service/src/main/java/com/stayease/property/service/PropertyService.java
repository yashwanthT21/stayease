package com.stayease.property.service;

import com.stayease.property.dto.PropertyRequest;
import com.stayease.property.dto.PropertyResponse;

import java.util.List;

/**
 * Business operations for properties.
 *
 * getAll takes optional ownerId / managerId filters:
 *   both null   = all properties
 *   ownerId set = only that owner's properties
 *   managerId set = only the properties assigned to that manager
 */
public interface PropertyService {

    PropertyResponse create(PropertyRequest request);

    List<PropertyResponse> getAll(Long ownerId, Long managerId);

    PropertyResponse getById(Long id);

    PropertyResponse update(Long id, PropertyRequest request);

    void delete(Long id);

    /** Existence check used by the availability & pricing services in this module. */
    boolean existsById(Long id);
}
