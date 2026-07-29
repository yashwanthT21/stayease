package com.stayease.booking.controller;

import com.stayease.booking.dto.GuestProfileRequest;
import com.stayease.booking.dto.GuestProfileResponse;
import com.stayease.booking.service.GuestProfileService;
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
 * REST endpoints for guest profiles under /api/guests.
 */
@RestController
@RequestMapping("/api/guests")
public class GuestProfileController {

    private final GuestProfileService service;

    public GuestProfileController(GuestProfileService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GuestProfileResponse> create(@Valid @RequestBody GuestProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<GuestProfileResponse>> getAll(
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(service.getAll(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GuestProfileResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GuestProfileResponse> update(
            @PathVariable Long id, @Valid @RequestBody GuestProfileRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
