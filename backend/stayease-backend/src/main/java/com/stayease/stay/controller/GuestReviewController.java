package com.stayease.stay.controller;

import com.stayease.stay.dto.GuestReviewRequest;
import com.stayease.stay.dto.GuestReviewResponse;
import com.stayease.stay.service.GuestReviewService;
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

@RestController
@RequestMapping("/api/reviews")
public class GuestReviewController {

    private final GuestReviewService service;

    public GuestReviewController(GuestReviewService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GuestReviewResponse> create(@Valid @RequestBody GuestReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<GuestReviewResponse>> getAll(
            @RequestParam(required = false) Long reservationId,
            @RequestParam(required = false) Long guestId) {
        return ResponseEntity.ok(service.getAll(reservationId, guestId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GuestReviewResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GuestReviewResponse> update(
            @PathVariable Long id, @Valid @RequestBody GuestReviewRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
