package com.stayease.property.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.property.client.NotificationClient;
import com.stayease.property.dto.PropertyRequest;
import com.stayease.property.dto.PropertyResponse;
import com.stayease.property.entity.Property;
import com.stayease.property.mapper.PropertyMapper;
import com.stayease.property.repository.AvailabilityCalendarRepository;
import com.stayease.property.repository.PricingRuleRepository;
import com.stayease.property.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for properties.
 *
 * As an extracted microservice this no longer calls IAM's UserService to verify
 * the owner exists: the user lives in another service's database, so ownerId /
 * managerId are soft references. On successful creation it notifies the owner via
 * notification-service (best-effort — see NotificationClient).
 */
@Service
@Transactional
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final AvailabilityCalendarRepository availabilityRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final NotificationClient notificationClient;

    public PropertyServiceImpl(PropertyRepository propertyRepository,
                               AvailabilityCalendarRepository availabilityRepository,
                               PricingRuleRepository pricingRuleRepository,
                               NotificationClient notificationClient) {
        this.propertyRepository = propertyRepository;
        this.availabilityRepository = availabilityRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.notificationClient = notificationClient;
    }

    @Override
    public PropertyResponse create(PropertyRequest request) {
        Property saved = propertyRepository.save(PropertyMapper.toEntity(request));
        // Inter-service side effect: tell the owner. Non-blocking to the result.
        notificationClient.notifyPropertyCreated(saved.getOwnerId(), saved.getTitle());
        return PropertyMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyResponse> getAll(Long ownerId, Long managerId) {
        List<Property> properties;
        if (managerId != null) {
            // managerId is a scope that must not be bypassable: when an ownerId is
            // also supplied, intersect the two rather than letting ownerId win.
            properties = (ownerId != null)
                    ? propertyRepository.findByOwnerIdAndManagerId(ownerId, managerId)
                    : propertyRepository.findByManagerId(managerId);
        } else if (ownerId != null) {
            properties = propertyRepository.findByOwnerId(ownerId);
        } else {
            properties = propertyRepository.findAll();
        }
        return properties.stream().map(PropertyMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyResponse getById(Long id) {
        return PropertyMapper.toResponse(findPropertyOrThrow(id));
    }

    @Override
    public PropertyResponse update(Long id, PropertyRequest request) {
        Property property = findPropertyOrThrow(id);
        PropertyMapper.updateEntity(property, request);
        return PropertyMapper.toResponse(propertyRepository.save(property));
    }

    /**
     * Deleting a property first removes its dependent availability rows and
     * pricing rules (same database, so we own them). Without this the DB foreign
     * keys would reject the delete and the client would get an opaque 500;
     * cascading here is both correct business behaviour and the fix for that bug.
     */
    @Override
    public void delete(Long id) {
        Property property = findPropertyOrThrow(id);
        availabilityRepository.deleteByPropertyId(id);
        pricingRuleRepository.deleteByPropertyId(id);
        propertyRepository.delete(property);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return id != null && propertyRepository.existsById(id);
    }

    /** 404 if the property id is unknown. */
    private Property findPropertyOrThrow(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found with id " + id));
    }
}
