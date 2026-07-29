package com.stayease.finance.controller;

import com.stayease.finance.dto.OwnerStatementRequest;
import com.stayease.finance.dto.OwnerStatementResponse;
import com.stayease.finance.service.OwnerStatementService;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
