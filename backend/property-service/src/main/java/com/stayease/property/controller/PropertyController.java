package com.stayease.property.controller;

import com.stayease.property.dto.PropertyRequest;
import com.stayease.property.dto.PropertyResponse;
import com.stayease.property.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for properties under /api/properties.
 */
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    /** POST /api/properties — create. 201 Created. */
    @PostMapping
    public ResponseEntity<PropertyResponse> create(@Valid @RequestBody PropertyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.create(request));
    }

    /**
     * GET /api/properties             -> all properties
     * GET /api/properties?ownerId=5   -> only owner 5's properties
     * GET /api/properties?managerId=7 -> only the properties assigned to manager 7
     *
     * Scoping for a PROPERTY_MANAGER is enforced here from the gateway-verified
     * identity headers (X-User-Role / X-User-Id), NOT from client query params —
     * so a manager can only ever see the properties assigned to them and cannot
     * widen the result by tampering with the request.
     */
    @GetMapping
    public ResponseEntity<List<PropertyResponse>> getAll(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long managerId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if ("PROPERTY_MANAGER".equalsIgnoreCase(userRole) && userId != null) {
            // Force the manager scope to the caller's own id; keep any ownerId as
            // an additional narrowing filter only.
            return ResponseEntity.ok(propertyService.getAll(ownerId, userId));
        }
        return ResponseEntity.ok(propertyService.getAll(ownerId, managerId));
    }

    /** GET /api/properties/{id} — one property. 200 or 404. */
    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getById(id));
    }

    /** PUT /api/properties/{id} — update. 200 or 404. */
    @PutMapping("/{id}")
    public ResponseEntity<PropertyResponse> update(
            @PathVariable Long id, @Valid @RequestBody PropertyRequest request) {
        return ResponseEntity.ok(propertyService.update(id, request));
    }

    /** DELETE /api/properties/{id} — delete (and its availability + pricing). 204. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        propertyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
