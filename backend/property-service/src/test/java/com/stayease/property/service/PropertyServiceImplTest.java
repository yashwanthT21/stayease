package com.stayease.property.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.property.client.NotificationClient;
import com.stayease.property.client.UserClient;
import com.stayease.property.dto.PropertyRequest;
import com.stayease.property.dto.PropertyResponse;
import com.stayease.property.entity.Property;
import com.stayease.property.enums.PropertyStatus;
import com.stayease.property.enums.PropertyType;
import com.stayease.property.repository.AvailabilityCalendarRepository;
import com.stayease.property.repository.PricingRuleRepository;
import com.stayease.property.repository.PropertyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for property-service's property logic.
 *
 * Two rules here are easy to get wrong and worth locking down:
 *   - a brand-new listing must not be publicly bookable, so it starts UNLISTED;
 *   - managerId is a SECURITY scope (a manager may only see their own properties),
 *     so passing an ownerId as well must narrow the result, never widen it.
 * Deleting a property also has to clean up its child rows first, or the database
 * foreign keys reject the delete and the client gets an opaque 500.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceImplTest {

    private static final Long OWNER_ID = 5L;
    private static final Long MANAGER_ID = 7L;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private AvailabilityCalendarRepository availabilityRepository;

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private PropertyServiceImpl service;

    private PropertyRequest request(PropertyStatus status) {
        return new PropertyRequest(OWNER_ID, MANAGER_ID, "Sea Breeze Villa", PropertyType.VILLA,
                "Kochi", 4, 2, 2, "Wi-Fi, Pool", "No smoking",
                LocalTime.of(14, 0), LocalTime.of(11, 0), status);
    }

    private Property entity(Long id) {
        Property property = new Property();
        property.setId(id);
        property.setOwnerId(OWNER_ID);
        property.setManagerId(MANAGER_ID);
        property.setTitle("Sea Breeze Villa");
        property.setType(PropertyType.VILLA);
        property.setCity("Kochi");
        property.setMaxGuests(4);
        property.setBedrooms(2);
        property.setBathrooms(2);
        property.setStatus(PropertyStatus.LISTED);
        return property;
    }

    @Test
    @DisplayName("create: a new listing starts UNLISTED so it isn't bookable by accident")
    void createDefaultsToUnlisted() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(call -> call.getArgument(0));

        PropertyResponse created = service.create(request(null));

        assertThat(created.status()).isEqualTo(PropertyStatus.UNLISTED);
        assertThat(created.checkInTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(created.checkOutTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("create: an owner publishing straight away gets LISTED")
    void createKeepsSuppliedStatus() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(call -> call.getArgument(0));

        PropertyResponse created = service.create(request(PropertyStatus.LISTED));

        assertThat(created.status()).isEqualTo(PropertyStatus.LISTED);
    }

    @Test
    @DisplayName("create: the owner is notified that their listing was created")
    void createNotifiesTheOwner() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(call -> call.getArgument(0));

        service.create(request(null));

        verify(notificationClient).notifyPropertyCreated(OWNER_ID, "Sea Breeze Villa");
    }

    @Test
    @DisplayName("create: a listing created with a manager already picked notifies that manager, naming the owner")
    void createNotifiesTheAssignedManager() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(call -> call.getArgument(0));
        when(userClient.findName(OWNER_ID)).thenReturn(Optional.of("Ada Owner"));

        service.create(request(null));

        verify(notificationClient).notifyManagerAssigned(MANAGER_ID, "Sea Breeze Villa", "Ada Owner");
    }

    @Test
    @DisplayName("create: an unresolvable owner name still sends the manager's notification")
    void createNotifiesTheManagerWithoutTheOwnerName() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(call -> call.getArgument(0));
        when(userClient.findName(OWNER_ID)).thenReturn(Optional.empty());

        service.create(request(null));

        // null name, not a dropped notification — the assignment is the news.
        verify(notificationClient).notifyManagerAssigned(MANAGER_ID, "Sea Breeze Villa", null);
    }

    @Test
    @DisplayName("update: handing a property to a DIFFERENT manager notifies the new one")
    void updateNotifiesANewlyAssignedManager() {
        Property property = entity(1L);
        property.setManagerId(99L); // previously managed by someone else
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(property));
        when(propertyRepository.save(any(Property.class))).thenAnswer(call -> call.getArgument(0));
        when(userClient.findName(OWNER_ID)).thenReturn(Optional.of("Ada Owner"));

        service.update(1L, request(PropertyStatus.LISTED));

        verify(notificationClient).notifyManagerAssigned(MANAGER_ID, "Sea Breeze Villa", "Ada Owner");
    }

    @Test
    @DisplayName("update: editing a property whose manager is unchanged does NOT nag the manager")
    void updateDoesNotRenotifyTheSameManager() {
        Property property = entity(1L); // already managed by MANAGER_ID
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(property));
        when(propertyRepository.save(any(Property.class))).thenAnswer(call -> call.getArgument(0));

        service.update(1L, request(PropertyStatus.LISTED));

        verify(notificationClient, never()).notifyManagerAssigned(any(), any(), any());
        // No point paying for an IAM round-trip when there's nothing to send.
        verify(userClient, never()).findName(any());
    }

    @Test
    @DisplayName("getAll: ownerId + managerId together NARROW the results (a manager scope can't be bypassed)")
    void getAllIntersectsOwnerAndManager() {
        when(propertyRepository.findByOwnerIdAndManagerId(OWNER_ID, MANAGER_ID))
                .thenReturn(List.of(entity(1L)));

        List<PropertyResponse> properties = service.getAll(OWNER_ID, MANAGER_ID);

        assertThat(properties).hasSize(1);
        // The ownerId must NOT win on its own — that would leak another manager's rows.
        verify(propertyRepository, never()).findByOwnerId(any());
        verify(propertyRepository, never()).findAll();
    }

    @Test
    @DisplayName("getAll: managerId alone scopes to that manager's properties")
    void getAllFiltersByManager() {
        when(propertyRepository.findByManagerId(MANAGER_ID)).thenReturn(List.of(entity(1L)));

        assertThat(service.getAll(null, MANAGER_ID)).hasSize(1);
        verify(propertyRepository, never()).findAll();
    }

    @Test
    @DisplayName("getAll: no filters returns everything (the admin view)")
    void getAllUnfilteredReturnsEverything() {
        when(propertyRepository.findAll()).thenReturn(List.of(entity(1L), entity(2L)));

        assertThat(service.getAll(null, null)).hasSize(2);
    }

    @Test
    @DisplayName("delete: child availability and pricing rows are removed BEFORE the property")
    void deleteCascadesChildRowsFirst() {
        Property property = entity(1L);
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(property));

        service.delete(1L);

        // Order matters: the database foreign keys would reject it the other way round.
        InOrder order = inOrder(availabilityRepository, pricingRuleRepository, propertyRepository);
        order.verify(availabilityRepository).deleteByPropertyId(1L);
        order.verify(pricingRuleRepository).deleteByPropertyId(1L);
        order.verify(propertyRepository).delete(property);
    }

    @Test
    @DisplayName("delete: an unknown id is a not-found error and deletes nothing")
    void deleteThrowsWhenMissing() {
        when(propertyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(availabilityRepository, never()).deleteByPropertyId(any());
        verify(propertyRepository, never()).delete(any(Property.class));
    }

    @Test
    @DisplayName("existsById: null id is false without a database call")
    void existsByIdHandlesNull() {
        assertThat(service.existsById(null)).isFalse();
        verify(propertyRepository, never()).existsById(any());
    }
}
