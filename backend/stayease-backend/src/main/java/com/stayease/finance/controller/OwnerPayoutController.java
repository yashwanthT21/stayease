package com.stayease.finance.controller;

import com.stayease.finance.dto.OwnerPayoutRequest;
import com.stayease.finance.dto.OwnerPayoutResponse;
import com.stayease.finance.service.OwnerPayoutService;
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
@RequestMapping("/api/owner-payouts")
public class OwnerPayoutController {

    private final OwnerPayoutService service;

    public OwnerPayoutController(OwnerPayoutService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OwnerPayoutResponse> create(@Valid @RequestBody OwnerPayoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OwnerPayoutResponse>> getAll(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long statementId) {
        return ResponseEntity.ok(service.getAll(ownerId, statementId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OwnerPayoutResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OwnerPayoutResponse> update(
            @PathVariable Long id, @Valid @RequestBody OwnerPayoutRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
