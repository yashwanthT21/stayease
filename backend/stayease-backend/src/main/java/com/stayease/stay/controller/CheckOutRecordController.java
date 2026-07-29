package com.stayease.stay.controller;

import com.stayease.stay.dto.CheckOutRecordRequest;
import com.stayease.stay.dto.CheckOutRecordResponse;
import com.stayease.stay.service.CheckOutRecordService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/check-outs")
public class CheckOutRecordController {

    private final CheckOutRecordService service;

    public CheckOutRecordController(CheckOutRecordService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CheckOutRecordResponse> create(@Valid @RequestBody CheckOutRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<CheckOutRecordResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CheckOutRecordResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CheckOutRecordResponse> update(
            @PathVariable Long id, @Valid @RequestBody CheckOutRecordRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
