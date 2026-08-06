package com.stayease.finance.service;

import com.stayease.common.client.NotificationClient;
import com.stayease.common.exception.ForbiddenOperationException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.finance.dto.OwnerStatementDecisionRequest;
import com.stayease.finance.dto.OwnerStatementRequest;
import com.stayease.finance.dto.OwnerStatementResponse;
import com.stayease.finance.entity.OwnerStatement;
import com.stayease.finance.enums.StatementStatus;
import com.stayease.finance.mapper.OwnerStatementMapper;
import com.stayease.finance.repository.OwnerStatementRepository;
import com.stayease.iam.dto.UserResponse;
import com.stayease.iam.enums.UserRole;
import com.stayease.iam.service.AuditService;
import com.stayease.iam.service.UserService;
import com.stayease.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Owner statements, including the owner's sign-off on them.
 *
 * The approve/reject pair is the money gate: a payout may only be created against
 * an APPROVED statement, so an owner is never paid on figures they haven't agreed
 * to (and a dispute can't be bypassed by Finance just issuing the payout anyway).
 * A rejection notifies Finance with the owner's reason, they correct the numbers
 * and re-issue (which clears the decision — see OwnerStatementMapper), and the
 * owner looks again.
 */
@Service
@Transactional
public class OwnerStatementServiceImpl implements OwnerStatementService {

    private static final String CATEGORY_PAYOUT = "PAYOUT";

    private final OwnerStatementRepository repository;
    private final UserService userService;
    private final AuditService auditService;
    private final NotificationClient notificationClient;
    private final CurrentUserService currentUserService;

    public OwnerStatementServiceImpl(OwnerStatementRepository repository,
                                     UserService userService,
                                     AuditService auditService,
                                     NotificationClient notificationClient,
                                     CurrentUserService currentUserService) {
        this.repository = repository;
        this.userService = userService;
        this.auditService = auditService;
        this.notificationClient = notificationClient;
        this.currentUserService = currentUserService;
    }

    @Override
    public OwnerStatementResponse create(OwnerStatementRequest request) {
        ensureOwnerExists(request.ownerId());
        OwnerStatement saved = repository.save(OwnerStatementMapper.toEntity(request));
        // Financial posting — record it in the audit trail.
        auditService.record(saved.getOwnerId(), "CREATE id=" + saved.getId(), "OwnerStatement");
        notifyOwnerIfIssued(saved);
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
        StatementStatus before = entity.getStatus();
        OwnerStatementMapper.updateEntity(entity, request);
        OwnerStatement saved = repository.save(entity);
        // Re-issuing a rejected statement is a fresh ask, so tell the owner again.
        if (before != StatementStatus.ISSUED && saved.getStatus() == StatementStatus.ISSUED) {
            notifyOwnerIfIssued(saved);
        }
        return OwnerStatementMapper.toResponse(saved);
    }

    @Override
    public OwnerStatementResponse approve(Long id, OwnerStatementDecisionRequest request) {
        OwnerStatement entity = findOrThrow(id);
        ensureIsTheirs(entity);
        ensureAwaitingDecision(entity, "approved");
        entity.setStatus(StatementStatus.APPROVED);
        entity.setOwnerNote(trimToNull(request == null ? null : request.note()));
        entity.setDecidedDate(LocalDateTime.now());
        OwnerStatement saved = repository.save(entity);

        auditService.record(saved.getOwnerId(), "OWNER_APPROVE id=" + saved.getId(), "OwnerStatement");
        notifyFinance("Owner " + ownerName(saved.getOwnerId()) + " APPROVED the "
                + saved.getPeriod() + " statement (#" + saved.getId() + "). "
                + "You can now release the payout." + noteSuffix(saved.getOwnerNote()));
        return OwnerStatementMapper.toResponse(saved);
    }

    @Override
    public OwnerStatementResponse reject(Long id, OwnerStatementDecisionRequest request) {
        OwnerStatement entity = findOrThrow(id);
        ensureIsTheirs(entity);
        ensureAwaitingDecision(entity, "rejected");
        String note = trimToNull(request == null ? null : request.note());
        if (note == null) {
            // A bare "no" gives Finance nothing to act on, and the whole point of a
            // rejection here is that it comes back corrected.
            throw new IllegalArgumentException(
                    "Please say what's wrong with the statement so Finance can correct it");
        }
        entity.setStatus(StatementStatus.REJECTED);
        entity.setOwnerNote(note);
        entity.setDecidedDate(LocalDateTime.now());
        OwnerStatement saved = repository.save(entity);

        auditService.record(saved.getOwnerId(), "OWNER_REJECT id=" + saved.getId(), "OwnerStatement");
        notifyFinance("Owner " + ownerName(saved.getOwnerId()) + " REJECTED the "
                + saved.getPeriod() + " statement (#" + saved.getId() + "). "
                + "Reason: " + note + " — please correct the figures and re-issue it.");
        return OwnerStatementMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isApproved(Long id) {
        return id != null && repository.findById(id)
                .map(s -> s.getStatus() == StatementStatus.APPROVED)
                .orElse(false);
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

    /**
     * An owner may only decide their OWN statement.
     *
     * SecurityConfig lets any OWNER reach these endpoints — it matches on the URL,
     * which carries no ownership information — so without this an owner could
     * approve a peer's statement and release someone else's money. Scoped to the
     * OWNER role: an ADMIN acting on an owner's behalf is legitimate, and an
     * unauthenticated caller never gets this far.
     */
    private void ensureIsTheirs(OwnerStatement statement) {
        if (!currentUserService.hasRole(UserRole.OWNER)) {
            return;
        }
        Long callerId = currentUserService.currentUserId().orElse(null);
        if (callerId == null || !callerId.equals(statement.getOwnerId())) {
            throw new ForbiddenOperationException("This statement belongs to another owner");
        }
    }

    /**
     * Only an ISSUED statement is the owner's to answer. A DRAFT hasn't been put to
     * them yet, and a PAID one is history — letting either be approved would make
     * the payout gate meaningless.
     */
    private void ensureAwaitingDecision(OwnerStatement statement, String verb) {
        if (statement.getStatus() != StatementStatus.ISSUED) {
            throw new IllegalArgumentException(
                    "Only an issued statement can be " + verb + " — this one is "
                            + statement.getStatus());
        }
    }

    private void ensureOwnerExists(Long ownerId) {
        if (!userService.existsById(ownerId)) {
            throw new ResourceNotFoundException("Owner (user) not found with id " + ownerId);
        }
    }

    // ---------------- notifications (best-effort side effects) ----------------

    /** Tell the owner there's a statement waiting for their approval. */
    private void notifyOwnerIfIssued(OwnerStatement statement) {
        if (statement.getStatus() != StatementStatus.ISSUED) {
            return; // a DRAFT isn't the owner's business yet
        }
        notificationClient.notifyUser(
                statement.getOwnerId(),
                "Your " + statement.getPeriod() + " statement is ready for review — net payout "
                        + statement.getNetPayout() + ". Approve it to release your payout, "
                        + "or reject it with a reason if the figures look wrong.",
                CATEGORY_PAYOUT);
    }

    /**
     * Fan the owner's decision out to every Finance user.
     *
     * There is no single "the financier" to address — Finance is a role, not a
     * person — so everyone who could act on it is told. Best-effort, like every
     * other notification: the decision is already saved.
     */
    private void notifyFinance(String message) {
        for (UserResponse financeUser : userService.getFinanceUsers()) {
            notificationClient.notifyUser(financeUser.id(), message, CATEGORY_PAYOUT);
        }
    }

    private String ownerName(Long ownerId) {
        try {
            return userService.getById(ownerId).name();
        } catch (RuntimeException ex) {
            return "#" + ownerId;
        }
    }

    /** " Note: …" when the owner left a comment, otherwise nothing. */
    private String noteSuffix(String note) {
        return note == null ? "" : " Note: " + note;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
