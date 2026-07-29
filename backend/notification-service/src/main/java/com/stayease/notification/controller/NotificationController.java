package com.stayease.notification.controller;

import com.stayease.notification.dto.NotificationRequest;
import com.stayease.notification.dto.NotificationResponse;
import com.stayease.notification.enums.NotificationStatus;
import com.stayease.notification.service.NotificationService;
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

/**
 * REST endpoints for notifications under /api/notifications.
 *
 * GET supports filters: ?userId=1 and/or ?status=UNREAD (either, both, or none).
 * PATCH /{id}/read and /{id}/dismiss are the lightweight status transitions a
 * user performs constantly, without having to resend the whole notification.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> create(@Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) NotificationStatus status) {
        return ResponseEntity.ok(service.getAll(userId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationResponse> update(
            @PathVariable Long id, @Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    /** PATCH /api/notifications/{id}/read — mark one notification as read. */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(service.markAsRead(id));
    }

    /** PATCH /api/notifications/{id}/dismiss — dismiss one notification. */
    @PatchMapping("/{id}/dismiss")
    public ResponseEntity<NotificationResponse> dismiss(@PathVariable Long id) {
        return ResponseEntity.ok(service.dismiss(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
