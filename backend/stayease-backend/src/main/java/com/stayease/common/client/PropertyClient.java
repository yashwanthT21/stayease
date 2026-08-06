package com.stayease.common.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to the property-service through {@link PropertyFeignClient} (a service
 * name resolved via Eureka + load balancer).
 *
 * This replaces the former in-process {@code PropertyService.existsById(...)}:
 * property now lives in its own service and database, so the booking,
 * housekeeping, and maintenance modules validate a propertyId with a remote call
 * instead of a local method. The exposed method keeps the same name/shape so the
 * callers barely change.
 *
 * The split is deliberate: the @FeignClient interface owns the HTTP contract,
 * this class owns the POLICY around it — which failures are fatal, which are
 * "treat as absent", and how several calls combine into one business operation.
 */
@Component
public class PropertyClient {

    private final PropertyFeignClient propertyFeignClient;

    public PropertyClient(PropertyFeignClient propertyFeignClient) {
        this.propertyFeignClient = propertyFeignClient;
    }

    /**
     * True if property-service reports a property with this id; false on 404.
     * A transport failure (service down) propagates, surfacing as a 5xx rather
     * than silently allowing a bad id.
     */
    public boolean existsById(Long id) {
        if (id == null) {
            return false;
        }
        try {
            return propertyFeignClient.getProperty(id) != null;
        } catch (FeignException.NotFound ex) {
            return false;
        }
    }

    /**
     * The property behind an id, or empty when it can't be fetched.
     *
     * Used to work out WHO to notify about something that happened to a property
     * (its owner and its assigned manager) and to name the property in the message.
     * Unlike {@link #existsById} this never throws: notifications are best-effort,
     * so a property-service blip must not fail the booking or review that triggered
     * the lookup.
     */
    public Optional<PropertySummary> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(propertyFeignClient.getProperty(id));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * Marks every night in [checkIn, checkOut) as BOOKED for a property — used
     * when a manager APPROVES a pending reservation (dates are held only on
     * approval). Fails fast with a 400 if any night is not currently AVAILABLE,
     * so an approval can never double-book a date.
     */
    public void markRangeBooked(Long propertyId, LocalDate checkIn, LocalDate checkOut) {
        List<AvailabilitySlot> slots = propertyFeignClient.listAvailability(propertyId);

        Map<LocalDate, AvailabilitySlot> byDate = new HashMap<>();
        if (slots != null) {
            for (AvailabilitySlot s : slots) {
                byDate.put(s.calendarDate(), s);
            }
        }

        // Verify the whole range is available BEFORE changing anything.
        List<AvailabilitySlot> toBook = new ArrayList<>();
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            AvailabilitySlot slot = byDate.get(d);
            if (slot == null || !"AVAILABLE".equals(slot.availabilityStatus())) {
                throw new IllegalArgumentException("Property is no longer available on " + d);
            }
            toBook.add(slot);
        }

        for (AvailabilitySlot slot : toBook) {
            Map<String, Object> body = new HashMap<>();
            body.put("propertyId", propertyId);
            body.put("calendarDate", slot.calendarDate().toString());
            body.put("availabilityStatus", "BOOKED");
            body.put("basePrice", slot.basePrice());
            body.put("minimumNights", slot.minimumNights() != null ? slot.minimumNights() : 1);
            propertyFeignClient.updateAvailability(slot.id(), body);
        }
    }

    /** Subset of property-service's availability row that we need here. */
    public record AvailabilitySlot(
            Long id,
            LocalDate calendarDate,
            String availabilityStatus,
            BigDecimal basePrice,
            Integer minimumNights) {
    }

    /**
     * Subset of property-service's PropertyResponse that the monolith needs: who
     * to notify (ownerId / managerId, the latter null when unassigned) and how to
     * describe the property in a message.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PropertySummary(
            Long id,
            Long ownerId,
            Long managerId,
            String title,
            String city,
            String checkInTime,
            String checkOutTime) {

        /** "Sea Breeze Villa (Kochi)" — or just the title when the city is unknown. */
        public String describe() {
            if (title == null) {
                return "property #" + id;
            }
            return city == null || city.isBlank() ? title : title + " (" + city + ")";
        }
    }
}
