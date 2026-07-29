package com.stayease.iam.mapper;

import com.stayease.iam.dto.AuditLogResponse;
import com.stayease.iam.entity.AuditLog;

public final class AuditLogMapper {

    private AuditLogMapper() {
    }

    public static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getEntityType(),
                log.getLoggedAt());
    }
}
