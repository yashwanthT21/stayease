package com.stayease.iam.dto;

import java.time.LocalDateTime;

/**
 * Read-only view of an audit entry. There is no AuditLogRequest because audit
 * entries are written by the SYSTEM (via AuditService.record), never posted by
 * a client.
 */
public record AuditLogResponse(
        Long id,
        Long userId,
        String action,
        String entityType,
        LocalDateTime loggedAt
) {
}
