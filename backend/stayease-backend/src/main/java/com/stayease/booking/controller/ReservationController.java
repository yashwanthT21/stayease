package com.stayease.booking.controller;

import com.stayease.booking.dto.ReservationRequest;
import com.stayease.booking.dto.ReservationResponse;
import com.stayease.booking.service.ReservationService;
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
 * REST endpoints for reservations under /api/reservations.
 *
 * GET supports two optional filters:
 *   /api/reservations?propertyId=2   -> reservations for a property
 *   /api/reservations?guestId=3      -> reservations for a guest
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getAll(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long guestId) {
        return ResponseEntity.ok(service.getAll(propertyId, guestId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReservationResponse> update(
            @PathVariable Long id, @Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
