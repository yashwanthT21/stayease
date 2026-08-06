package com.stayease.property.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.property.client.NotificationClient;
import com.stayease.property.client.UserClient;
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
import java.util.Objects;

/**
 * Business logic for properties.
 *
 * As an extracted microservice this no longer calls IAM's UserService to verify
 * the owner exists: the user lives in another service's database, so ownerId /
 * managerId are soft references.
 *
 * Two notifications originate here, both best-effort side effects (see
 * NotificationClient) that never block or roll back the write:
 *   creating a listing  → the owner is told it exists;
 *   assigning a manager → THAT manager is told the property is now theirs, naming
 *                         the owner who handed it over.
 * The manager notification fires on create as well as update, because an owner can
 * pick a manager in the same form that creates the listing — and only when the
 * assignment actually CHANGED, so re-saving an unrelated field (a new price, a
 * typo in the house rules) doesn't nag the manager every time.
 */
@Service
@Transactional
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final AvailabilityCalendarRepository availabilityRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final NotificationClient notificationClient;
    private final UserClient userClient;

    public PropertyServiceImpl(PropertyRepository propertyRepository,
                               AvailabilityCalendarRepository availabilityRepository,
                               PricingRuleRepository pricingRuleRepository,
                               NotificationClient notificationClient,
                               UserClient userClient) {
        this.propertyRepository = propertyRepository;
        this.availabilityRepository = availabilityRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.notificationClient = notificationClient;
        this.userClient = userClient;
    }

    @Override
    public PropertyResponse create(PropertyRequest request) {
        Property saved = propertyRepository.save(PropertyMapper.toEntity(request));
        // Inter-service side effects: tell the owner, and the manager if the
        // listing was created with one already assigned.
        notificationClient.notifyPropertyCreated(saved.getOwnerId(), saved.getTitle());
        notifyManagerIfNewlyAssigned(saved, null);
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
        // Read the outgoing manager BEFORE the mapper overwrites it — that's the
        // only way to tell an actual reassignment from an unrelated edit.
        Long previousManagerId = property.getManagerId();
        PropertyMapper.updateEntity(property, request);
        Property saved = propertyRepository.save(property);
        notifyManagerIfNewlyAssigned(saved, previousManagerId);
        return PropertyMapper.toResponse(saved);
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

    /**
     * Tell the assigned manager the property is now theirs — but only when this
     * write actually handed it to someone new.
     *
     * Skipped when there is no manager (unassigning is not news for the person who
     * just lost it) and when the manager is unchanged, so an owner editing the
     * title of an already-managed property sends nothing. The owner's name is
     * fetched from IAM for the message; a failed lookup still sends the
     * notification, worded generically.
     */
    private void notifyManagerIfNewlyAssigned(Property property, Long previousManagerId) {
        Long managerId = property.getManagerId();
        if (managerId == null || Objects.equals(managerId, previousManagerId)) {
            return;
        }
        String ownerName = userClient.findName(property.getOwnerId()).orElse(null);
        notificationClient.notifyManagerAssigned(managerId, property.getTitle(), ownerName);
    }

    /** 404 if the property id is unknown. */
    private Property findPropertyOrThrow(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found with id " + id));
    }
}
