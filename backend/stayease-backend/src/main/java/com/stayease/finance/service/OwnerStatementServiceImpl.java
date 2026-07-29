package com.stayease.finance.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.finance.dto.OwnerStatementRequest;
import com.stayease.finance.dto.OwnerStatementResponse;
import com.stayease.finance.entity.OwnerStatement;
import com.stayease.finance.mapper.OwnerStatementMapper;
import com.stayease.finance.repository.OwnerStatementRepository;
import com.stayease.finance.entity.OwnerStatement;
import com.stayease.iam.service.AuditService;
import com.stayease.iam.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OwnerStatementServiceImpl implements OwnerStatementService {

    private final OwnerStatementRepository repository;
    private final UserService userService;
    private final AuditService auditService;

    public OwnerStatementServiceImpl(OwnerStatementRepository repository,
                                     UserService userService,
                                     AuditService auditService) {
        this.repository = repository;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Override
    public OwnerStatementResponse create(OwnerStatementRequest request) {
        ensureOwnerExists(request.ownerId());
        OwnerStatement saved = repository.save(OwnerStatementMapper.toEntity(request));
        // Financial posting — record it in the audit trail.
        auditService.record(saved.getOwnerId(), "CREATE id=" + saved.getId(), "OwnerStatement");
        return OwnerStatementMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnerStatementResponse> getAll(Long ownerId) {
        List<OwnerStatement> list = (ownerId == null)
                ? repository.findAll()
                : repository.findByOwnerId(ownerId);
        return list.stream().map(OwnerStatementMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerStatementResponse getById(Long id) {
        return OwnerStatementMapper.toResponse(findOrThrow(id));
    }

    @Override
    public OwnerStatementResponse update(Long id, OwnerStatementRequest request) {
        OwnerStatement entity = findOrThrow(id);
        ensureOwnerExists(request.ownerId());
        OwnerStatementMapper.updateEntity(entity, request);
        return OwnerStatementMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return id != null && repository.existsById(id);
    }

    private OwnerStatement findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Owner statement not found with id " + id));
    }

    private void ensureOwnerExists(Long ownerId) {
        if (!userService.existsById(ownerId)) {
            throw new ResourceNotFoundException("Owner (user) not found with id " + ownerId);
        }
    }
}
