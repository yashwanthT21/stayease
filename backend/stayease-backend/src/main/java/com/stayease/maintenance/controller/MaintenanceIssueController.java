package com.stayease.maintenance.controller;

import com.stayease.maintenance.dto.MaintenanceIssueRequest;
import com.stayease.maintenance.dto.MaintenanceIssueResponse;
import com.stayease.maintenance.enums.MaintenanceStatus;
import com.stayease.maintenance.service.MaintenanceIssueService;
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
 * GET supports filters: ?propertyId=1  or  ?status=OPEN
 */
@RestController
@RequestMapping("/api/maintenance-issues")
public class MaintenanceIssueController {

    private final MaintenanceIssueService service;

    public MaintenanceIssueController(MaintenanceIssueService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MaintenanceIssueResponse> create(
            @Valid @RequestBody MaintenanceIssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<MaintenanceIssueResponse>> getAll(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) MaintenanceStatus status) {
        return ResponseEntity.ok(service.getAll(propertyId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceIssueResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceIssueResponse> update(
            @PathVariable Long id, @Valid @RequestBody MaintenanceIssueRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
