package com.stayease.finance.controller;

import com.stayease.finance.dto.OwnerStatementDecisionRequest;
import com.stayease.finance.dto.OwnerStatementRequest;
import com.stayease.finance.dto.OwnerStatementResponse;
import com.stayease.finance.service.OwnerStatementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner-statements")
public class OwnerStatementController {

    private final OwnerStatementService service;

    public OwnerStatementController(OwnerStatementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OwnerStatementResponse> create(
            @Valid @RequestBody OwnerStatementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OwnerStatementResponse>> getAll(
            @RequestParam(required = false) Long ownerId) {
        return ResponseEntity.ok(service.getAll(ownerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OwnerStatementResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OwnerStatementResponse> update(
            @PathVariable Long id, @Valid @RequestBody OwnerStatementRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    /**
     * PATCH /api/owner-statements/{id}/approve — the OWNER accepts the figures.
     *
     * This is the gate on the money: until it has been called, POST
     * /api/owner-statements/../payouts is refused. The body (an optional note) is
     * itself optional, so an owner can approve with a bare PATCH.
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<OwnerStatementResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) OwnerStatementDecisionRequest request) {
        return ResponseEntity.ok(service.approve(id, request));
    }

    /**
     * PATCH /api/owner-statements/{id}/reject — the OWNER disputes the figures.
     *
     * A reason is required (the service rejects a blank one): it's what Finance
     * gets notified with and works from when re-issuing.
     */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<OwnerStatementResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) OwnerStatementDecisionRequest request) {
        return ResponseEntity.ok(service.reject(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
