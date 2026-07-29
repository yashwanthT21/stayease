package com.stayease.iam.service;

import com.stayease.iam.dto.AuditLogResponse;

import java.util.List;

/**
 * The audit trail. record() is called BY other services to log a sensitive
 * action; the read methods back the /api/audit-logs endpoints.
 */
public interface AuditService {

    /**
     * Append an audit entry. Joins the caller's transaction, so if the audited
     * operation rolls back, the audit row rolls back with it (we never log an
     * action that didn't actually happen).
     */
    void record(Long userId, String action, String entityType);

    List<AuditLogResponse> getAll(Long userId, String entityType);

    AuditLogResponse getById(Long id);
}
