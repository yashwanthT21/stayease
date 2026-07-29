package com.stayease.housekeeping.controller;

import com.stayease.housekeeping.dto.TurnoverChecklistRequest;
import com.stayease.housekeeping.dto.TurnoverChecklistResponse;
import com.stayease.housekeeping.service.TurnoverChecklistService;
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
 * Checklist items are always scoped to a turnover:
 * GET /api/checklists?turnoverId=1
 */
@RestController
@RequestMapping("/api/checklists")
public class TurnoverChecklistController {

    private final TurnoverChecklistService service;

    public TurnoverChecklistController(TurnoverChecklistService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TurnoverChecklistResponse> create(
            @Valid @RequestBody TurnoverChecklistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<TurnoverChecklistResponse>> getByTurnover(
            @RequestParam Long turnoverId) {
        return ResponseEntity.ok(service.getByTurnover(turnoverId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TurnoverChecklistResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TurnoverChecklistResponse> update(
            @PathVariable Long id, @Valid @RequestBody TurnoverChecklistRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
