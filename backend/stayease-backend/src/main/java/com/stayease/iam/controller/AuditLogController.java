package com.stayease.iam.controller;

import com.stayease.iam.dto.AuditLogResponse;
import com.stayease.iam.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * READ-ONLY audit trail under /api/audit-logs. There are deliberately no
 * POST/PUT/DELETE endpoints — audit entries are written by the system and must
 * not be editable or deletable through the API.
 *
 * Filters: ?userId=1  or  ?entityType=OwnerStatement
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditService service;

    public AuditLogController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getAll(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entityType) {
        return ResponseEntity.ok(service.getAll(userId, entityType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }
}
