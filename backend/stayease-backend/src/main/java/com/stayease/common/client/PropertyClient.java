package com.stayease.common.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
}
