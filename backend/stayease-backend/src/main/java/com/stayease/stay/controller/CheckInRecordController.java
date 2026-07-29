package com.stayease.stay.controller;

import com.stayease.stay.dto.CheckInRecordRequest;
import com.stayease.stay.dto.CheckInRecordResponse;
import com.stayease.stay.service.CheckInRecordService;
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
@RequestMapping("/api/check-ins")
public class CheckInRecordController {

    private final CheckInRecordService service;

    public CheckInRecordController(CheckInRecordService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CheckInRecordResponse> create(@Valid @RequestBody CheckInRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<CheckInRecordResponse>> getAll(
            @RequestParam(required = false) Long guestId) {
        return ResponseEntity.ok(service.getAll(guestId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CheckInRecordResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CheckInRecordResponse> update(
            @PathVariable Long id, @Valid @RequestBody CheckInRecordRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
