package com.stayease.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Declarative HTTP contract for property-service.
 *
 * "property-service" is the name the service registers under in Eureka, not a
 * host: Feign hands it to Spring Cloud LoadBalancer, which swaps in a live
 * instance's host:port. Nothing here says where property-service runs.
 *
 * This interface is the raw transport only — one method per remote endpoint,
 * exceptions and all. The business-friendly wrapper (best-effort lookups, the
 * "book this whole range" workflow) lives in {@link PropertyClient}, which is what
 * the rest of the app injects.
 *
 * To point at a fixed host instead of discovery (an environment without Eureka),
 * set spring.cloud.openfeign.client.config.property-service.url.
 */
@FeignClient(name = "property-service")
public interface PropertyFeignClient {

    /** GET /api/properties/{id} — throws FeignException.NotFound (404) for an unknown id. */
    @GetMapping("/api/properties/{id}")
    PropertyClient.PropertySummary getProperty(@PathVariable("id") Long id);

    /** GET /api/availability?propertyId=… — every calendar row for one property. */
    @GetMapping("/api/availability")
    List<PropertyClient.AvailabilitySlot> listAvailability(@RequestParam("propertyId") Long propertyId);

    /**
     * PUT /api/availability/{id} — replaces one calendar row. The body is a plain
     * Map because we only resend the handful of fields property-service requires,
     * and a DTO here would have to track its whole request shape.
     */
    @PutMapping("/api/availability/{id}")
    void updateAvailability(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);
}
