package com.stayease.housekeeping.controller;

import com.stayease.housekeeping.dto.TurnoverAssignmentRequest;
import com.stayease.housekeeping.dto.TurnoverAssignmentResponse;
import com.stayease.housekeeping.service.TurnoverAssignmentService;
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
@RequestMapping("/api/turnovers")
public class TurnoverAssignmentController {

    private final TurnoverAssignmentService service;

    public TurnoverAssignmentController(TurnoverAssignmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TurnoverAssignmentResponse> create(
            @Valid @RequestBody TurnoverAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<TurnoverAssignmentResponse>> getAll(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long assignedToId) {
        return ResponseEntity.ok(service.getAll(propertyId, assignedToId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TurnoverAssignmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TurnoverAssignmentResponse> update(
            @PathVariable Long id, @Valid @RequestBody TurnoverAssignmentRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
