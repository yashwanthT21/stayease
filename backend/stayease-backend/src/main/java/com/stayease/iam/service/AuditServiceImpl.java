package com.stayease.iam.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.iam.dto.AuditLogResponse;
import com.stayease.iam.entity.AuditLog;
import com.stayease.iam.mapper.AuditLogMapper;
import com.stayease.iam.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository repository;

    public AuditServiceImpl(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(Long userId, String action, String entityType) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setLoggedAt(LocalDateTime.now()); // server-stamped
        repository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAll(Long userId, String entityType) {
        List<AuditLog> logs;
        if (userId != null) {
            logs = repository.findByUserId(userId);
        } else if (entityType != null) {
            logs = repository.findByEntityType(entityType);
        } else {
            logs = repository.findAll();
        }
        return logs.stream().map(AuditLogMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse getById(Long id) {
        return repository.findById(id)
                .map(AuditLogMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log not found with id " + id));
    }
}
