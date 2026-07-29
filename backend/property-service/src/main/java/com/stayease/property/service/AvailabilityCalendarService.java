package com.stayease.property.service;

import com.stayease.property.dto.AvailabilityCalendarRequest;
import com.stayease.property.dto.AvailabilityCalendarResponse;

import java.util.List;

public interface AvailabilityCalendarService {

    AvailabilityCalendarResponse create(AvailabilityCalendarRequest request);

    List<AvailabilityCalendarResponse> getByProperty(Long propertyId);

    AvailabilityCalendarResponse getById(Long id);

    AvailabilityCalendarResponse update(Long id, AvailabilityCalendarRequest request);

    void delete(Long id);
}
