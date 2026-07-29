package com.stayease.common.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to the property-service over HTTP (resolved via Eureka + load balancer).
 *
 * This replaces the former in-process {@code PropertyService.existsById(...)}:
 * property now lives in its own service and database, so the booking,
 * housekeeping, and maintenance modules validate a propertyId with a remote call
 * instead of a local method. The exposed method keeps the same name/shape so the
 * callers barely change.
 */
@Component
public class PropertyClient {

    private final RestClient propertyRestClient;

    public PropertyClient(RestClient propertyRestClient) {
        this.propertyRestClient = propertyRestClient;
    }

    /**
     * True if property-service reports a property with this id (HTTP 2xx on
     * GET /api/properties/{id}); false on 404. A transport failure (service down)
     * propagates, surfacing as a 5xx rather than silently allowing a bad id.
     */
    public boolean existsById(Long id) {
        if (id == null) {
            return false;
        }
        return Boolean.TRUE.equals(
                propertyRestClient.get()
                        .uri("/api/properties/{id}", id)
                        .exchange((request, response) -> response.getStatusCode().is2xxSuccessful()));
    }

    /**
     * Marks every night in [checkIn, checkOut) as BOOKED for a property — used
     * when a manager APPROVES a pending reservation (dates are held only on
     * approval). Fails fast with a 400 if any night is not currently AVAILABLE,
     * so an approval can never double-book a date.
     */
    public void markRangeBooked(Long propertyId, LocalDate checkIn, LocalDate checkOut) {
        List<AvailabilitySlot> slots = propertyRestClient.get()
                .uri(b -> b.path("/api/availability").queryParam("propertyId", propertyId).build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<AvailabilitySlot>>() {});

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
            propertyRestClient.put()
                    .uri("/api/availability/{id}", slot.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
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
}
