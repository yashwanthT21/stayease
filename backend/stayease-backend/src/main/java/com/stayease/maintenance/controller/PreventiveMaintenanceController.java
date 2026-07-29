package com.stayease.maintenance.controller;

import com.stayease.maintenance.dto.PreventiveMaintenanceRequest;
import com.stayease.maintenance.dto.PreventiveMaintenanceResponse;
import com.stayease.maintenance.service.PreventiveMaintenanceService;
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
@RequestMapping("/api/preventive-maintenance")
public class PreventiveMaintenanceController {

    private final PreventiveMaintenanceService service;

    public PreventiveMaintenanceController(PreventiveMaintenanceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PreventiveMaintenanceResponse> create(
            @Valid @RequestBody PreventiveMaintenanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<PreventiveMaintenanceResponse>> getAll(
            @RequestParam(required = false) Long propertyId) {
        return ResponseEntity.ok(service.getAll(propertyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreventiveMaintenanceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PreventiveMaintenanceResponse> update(
            @PathVariable Long id, @Valid @RequestBody PreventiveMaintenanceRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
