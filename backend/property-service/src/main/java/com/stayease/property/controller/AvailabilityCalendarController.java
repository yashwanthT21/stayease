package com.stayease.property.controller;

import com.stayease.property.dto.AvailabilityCalendarRequest;
import com.stayease.property.dto.AvailabilityCalendarResponse;
import com.stayease.property.service.AvailabilityCalendarService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for availability rows under /api/availability.
 *
 * Listing is always scoped to a property: GET /api/availability?propertyId=1
 * (returns 404 if that property doesn't exist).
 */
@RestController
@RequestMapping("/api/availability")
public class AvailabilityCalendarController {

    private final AvailabilityCalendarService service;

    public AvailabilityCalendarController(AvailabilityCalendarService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AvailabilityCalendarResponse> create(
            @Valid @RequestBody AvailabilityCalendarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<AvailabilityCalendarResponse>> getByProperty(
            @RequestParam Long propertyId) {
        return ResponseEntity.ok(service.getByProperty(propertyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AvailabilityCalendarResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AvailabilityCalendarResponse> update(
            @PathVariable Long id, @Valid @RequestBody AvailabilityCalendarRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
