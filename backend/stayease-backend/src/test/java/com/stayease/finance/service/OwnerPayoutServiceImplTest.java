package com.stayease.finance.service;

import com.stayease.finance.dto.OwnerPayoutRequest;
import com.stayease.finance.entity.OwnerPayout;
import com.stayease.finance.enums.PayoutStatus;
import com.stayease.finance.repository.OwnerPayoutRepository;
import com.stayease.iam.service.AuditService;
import com.stayease.iam.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for owner payouts.
 *
 * The rule worth pinning down is the GATE: a payout is money leaving, and it may
 * only be created against a statement the owner has approved. It is enforced in the
 * service rather than only in the UI, so these tests are what stop a refactor from
 * quietly reopening the path where Finance releases a payout on figures the owner
 * disputed.
 */
@ExtendWith(MockitoExtension.class)
class OwnerPayoutServiceImplTest {

    private static final Long OWNER_ID = 5L;
    private static final Long STATEMENT_ID = 11L;

    @Mock
    private OwnerPayoutRepository repository;

    @Mock
    private OwnerStatementService ownerStatementService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private OwnerPayoutServiceImpl service;

    private OwnerPayoutRequest request() {
        return new OwnerPayoutRequest(STATEMENT_ID, OWNER_ID, new BigDecimal("42750.00"),
                LocalDate.of(2026, 9, 1), "HDFC-****1234", null);
    }

    @Test
    @DisplayName("create: an APPROVED statement can be paid out")
    void createSucceedsForAnApprovedStatement() {
        when(ownerStatementService.existsById(STATEMENT_ID)).thenReturn(true);
        when(userService.existsById(OWNER_ID)).thenReturn(true);
        when(ownerStatementService.isApproved(STATEMENT_ID)).thenReturn(true);
        when(repository.save(any(OwnerPayout.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.create(request()).status()).isEqualTo(PayoutStatus.PENDING);
    }

    @Test
    @DisplayName("create: BLOCKED while the owner hasn't approved the statement")
    void createIsBlockedUntilTheOwnerApproves() {
        when(ownerStatementService.existsById(STATEMENT_ID)).thenReturn(true);
        when(userService.existsById(OWNER_ID)).thenReturn(true);
        when(ownerStatementService.isApproved(STATEMENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approved by the owner");

        // Nothing is written and nothing is audited: the payout never existed.
        verify(repository, never()).save(any(OwnerPayout.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("update: an existing payout stays editable regardless of the statement's state")
    void updateIsNotGated() {
        OwnerPayout existing = new OwnerPayout();
        existing.setId(1L);
        existing.setStatementId(STATEMENT_ID);
        existing.setOwnerId(OWNER_ID);
        existing.setAmount(new BigDecimal("42750.00"));
        existing.setStatus(PayoutStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(ownerStatementService.existsById(STATEMENT_ID)).thenReturn(true);
        when(userService.existsById(OWNER_ID)).thenReturn(true);
        when(repository.save(any(OwnerPayout.class))).thenAnswer(call -> call.getArgument(0));

        // Marking a released payout PAID must not be blocked by a later change to
        // the statement — the gate is on CREATING the payout, not on maintaining it.
        service.update(1L, new OwnerPayoutRequest(STATEMENT_ID, OWNER_ID,
                new BigDecimal("42750.00"), LocalDate.of(2026, 9, 1), "HDFC-****1234",
                PayoutStatus.PAID));

        verify(ownerStatementService, never()).isApproved(any());
    }
}
