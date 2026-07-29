package com.stayease.property.service;

import com.stayease.common.exception.DuplicateResourceException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.property.dto.AvailabilityCalendarRequest;
import com.stayease.property.dto.AvailabilityCalendarResponse;
import com.stayease.property.entity.AvailabilityCalendar;
import com.stayease.property.mapper.AvailabilityCalendarMapper;
import com.stayease.property.repository.AvailabilityCalendarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for per-date availability.
 *
 * Depends on PropertyService (same module) to confirm the property exists, and
 * enforces one row per (property, date).
 */
@Service
@Transactional
public class AvailabilityCalendarServiceImpl implements AvailabilityCalendarService {

    private final AvailabilityCalendarRepository repository;
    private final PropertyService propertyService;

    public AvailabilityCalendarServiceImpl(AvailabilityCalendarRepository repository,
                                           PropertyService propertyService) {
        this.repository = repository;
        this.propertyService = propertyService;
    }

    @Override
    public AvailabilityCalendarResponse create(AvailabilityCalendarRequest request) {
        ensurePropertyExists(request.propertyId());

        // One availability row per property per date.
        repository.findByPropertyIdAndCalendarDate(request.propertyId(), request.calendarDate())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Availability already exists for property " + request.propertyId()
                                    + " on " + request.calendarDate());
                });

        AvailabilityCalendar saved = repository.save(AvailabilityCalendarMapper.toEntity(request));
        return AvailabilityCalendarMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilityCalendarResponse> getByProperty(Long propertyId) {
        ensurePropertyExists(propertyId);
        return repository.findByPropertyId(propertyId)
                .stream().map(AvailabilityCalendarMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityCalendarResponse getById(Long id) {
        return AvailabilityCalendarMapper.toResponse(findOrThrow(id));
    }

    @Override
    public AvailabilityCalendarResponse update(Long id, AvailabilityCalendarRequest request) {
        AvailabilityCalendar entity = findOrThrow(id);

        // The row stays on its own property (propertyId is immutable on update);
        // the uniqueness check therefore runs against the entity's property, not
        // whatever propertyId the request carried.
        Long propertyId = entity.getPropertyId();
        repository.findByPropertyIdAndCalendarDate(propertyId, request.calendarDate())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException(
                            "Availability already exists for property " + propertyId
                                    + " on " + request.calendarDate());
                });

        AvailabilityCalendarMapper.updateEntity(entity, request);
        return AvailabilityCalendarMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private AvailabilityCalendar findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Availability record not found with id " + id));
    }

    private void ensurePropertyExists(Long propertyId) {
        if (!propertyService.existsById(propertyId)) {
            throw new ResourceNotFoundException("Property not found with id " + propertyId);
        }
    }
}
