package com.stayease.finance.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.finance.dto.OwnerPayoutRequest;
import com.stayease.finance.dto.OwnerPayoutResponse;
import com.stayease.finance.entity.OwnerPayout;
import com.stayease.finance.mapper.OwnerPayoutMapper;
import com.stayease.finance.repository.OwnerPayoutRepository;
import com.stayease.finance.entity.OwnerPayout;
import com.stayease.iam.service.AuditService;
import com.stayease.iam.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owner payouts.
 *
 * A payout is the money actually leaving, so it is gated on the owner having
 * APPROVED the statement it pays against (see
 * {@link OwnerStatementService#isApproved}). That check lives here, on the write
 * path, rather than only being hidden in the UI — otherwise Finance could release
 * a payout on figures an owner had rejected simply by calling the API directly.
 */
@Service
@Transactional
public class OwnerPayoutServiceImpl implements OwnerPayoutService {

    private final OwnerPayoutRepository repository;
    private final OwnerStatementService ownerStatementService;
    private final UserService userService;
    private final AuditService auditService;

    public OwnerPayoutServiceImpl(OwnerPayoutRepository repository,
                                  OwnerStatementService ownerStatementService,
                                  UserService userService,
                                  AuditService auditService) {
        this.repository = repository;
        this.ownerStatementService = ownerStatementService;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Override
    public OwnerPayoutResponse create(OwnerPayoutRequest request) {
        validateReferences(request);
        ensureStatementApproved(request.statementId());
        OwnerPayout saved = repository.save(OwnerPayoutMapper.toEntity(request));
        // Financial posting — record it in the audit trail.
        auditService.record(saved.getOwnerId(), "ISSUE_PAYOUT id=" + saved.getId(), "OwnerPayout");
        return OwnerPayoutMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnerPayoutResponse> getAll(Long ownerId, Long statementId) {
        List<OwnerPayout> list;
        if (statementId != null) {
            list = repository.findByStatementId(statementId);
        } else if (ownerId != null) {
            list = repository.findByOwnerId(ownerId);
        } else {
            list = repository.findAll();
        }
        return list.stream().map(OwnerPayoutMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerPayoutResponse getById(Long id) {
        return OwnerPayoutMapper.toResponse(findOrThrow(id));
    }

    @Override
    public OwnerPayoutResponse update(Long id, OwnerPayoutRequest request) {
        OwnerPayout entity = findOrThrow(id);
        validateReferences(request);
        OwnerPayoutMapper.updateEntity(entity, request);
        return OwnerPayoutMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private OwnerPayout findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Owner payout not found with id " + id));
    }

    private void validateReferences(OwnerPayoutRequest request) {
        if (!ownerStatementService.existsById(request.statementId())) {
            throw new ResourceNotFoundException(
                    "Owner statement not found with id " + request.statementId());
        }
        if (!userService.existsById(request.ownerId())) {
            throw new ResourceNotFoundException("Owner (user) not found with id " + request.ownerId());
        }
    }

    /**
     * Refuse to pay out against a statement the owner hasn't signed off.
     *
     * Only enforced when CREATING a payout: once one exists, editing it (fixing a
     * bank reference, marking it PAID) must not be blocked by a later change to the
     * statement's state.
     */
    private void ensureStatementApproved(Long statementId) {
        if (!ownerStatementService.isApproved(statementId)) {
            throw new IllegalArgumentException(
                    "Statement #" + statementId + " has not been approved by the owner yet — "
                            + "a payout can only be released once they approve it");
        }
    }
}
