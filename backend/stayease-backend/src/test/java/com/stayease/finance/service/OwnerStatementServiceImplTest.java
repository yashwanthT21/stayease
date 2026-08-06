package com.stayease.finance.service;

import com.stayease.common.client.NotificationClient;
import com.stayease.common.exception.ForbiddenOperationException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.finance.dto.OwnerStatementDecisionRequest;
import com.stayease.finance.dto.OwnerStatementRequest;
import com.stayease.finance.dto.OwnerStatementResponse;
import com.stayease.finance.entity.OwnerStatement;
import com.stayease.finance.enums.StatementStatus;
import com.stayease.finance.repository.OwnerStatementRepository;
import com.stayease.iam.dto.UserResponse;
import com.stayease.iam.enums.UserRole;
import com.stayease.iam.enums.UserStatus;
import com.stayease.iam.service.AuditService;
import com.stayease.iam.service.UserService;
import com.stayease.security.CurrentUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the finance module's owner-statement service.
 *
 * This is the money path, so the arithmetic is the point:
 *   netPayout = grossRevenue + cleaningRevenue
 *               - platformFee - managementFee - maintenanceCost
 * The client never sends netPayout — the server computes it, which is exactly the
 * kind of rule that deserves a test rather than a comment. Every statement is also
 * written to the audit trail, since it's a financial posting.
 */
@ExtendWith(MockitoExtension.class)
class OwnerStatementServiceImplTest {

    private static final Long OWNER_ID = 5L;

    @Mock
    private OwnerStatementRepository repository;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private OwnerStatementServiceImpl service;

    /** An ISSUED statement, i.e. one sitting on the owner's desk awaiting an answer. */
    private OwnerStatement issued(Long id) {
        OwnerStatement statement = new OwnerStatement();
        statement.setId(id);
        statement.setOwnerId(OWNER_ID);
        statement.setPeriod("2026-08");
        statement.setNetPayout(new BigDecimal("42750.00"));
        statement.setStatus(StatementStatus.ISSUED);
        return statement;
    }

    private UserResponse financeUser(Long id) {
        return new UserResponse(id, "Fay Finance", "fay@example.com", "555-0199",
                UserRole.FINANCE, UserStatus.ACTIVE);
    }

    private OwnerStatementRequest request(String gross, String platform, String management,
                                          String cleaning, String maintenance) {
        return new OwnerStatementRequest(OWNER_ID, "2026-08",
                gross == null ? null : new BigDecimal(gross),
                platform == null ? null : new BigDecimal(platform),
                management == null ? null : new BigDecimal(management),
                cleaning == null ? null : new BigDecimal(cleaning),
                maintenance == null ? null : new BigDecimal(maintenance),
                null);
    }

    @Test
    @DisplayName("create: net payout = income minus every deduction")
    void createComputesNetPayout() {
        when(userService.existsById(OWNER_ID)).thenReturn(true);
        when(repository.save(any(OwnerStatement.class))).thenAnswer(call -> call.getArgument(0));

        // 50000 gross + 3000 cleaning - 5000 platform - 4000 management - 1250 maintenance
        OwnerStatementResponse created =
                service.create(request("50000.00", "5000.00", "4000.00", "3000.00", "1250.00"));

        assertThat(created.netPayout()).isEqualByComparingTo("42750.00");
        assertThat(created.status()).isEqualTo(StatementStatus.DRAFT);
        assertThat(created.generatedDate()).isNotNull();
    }

    @Test
    @DisplayName("create: missing amounts count as zero rather than blowing up")
    void createTreatsMissingAmountsAsZero() {
        when(userService.existsById(OWNER_ID)).thenReturn(true);
        when(repository.save(any(OwnerStatement.class))).thenAnswer(call -> call.getArgument(0));

        // Only gross revenue supplied — everything else absent.
        OwnerStatementResponse created = service.create(request("20000.00", null, null, null, null));

        assertThat(created.netPayout()).isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("create: deductions larger than income give a negative payout (owner owes money)")
    void createAllowsNegativeNetPayout() {
        when(userService.existsById(OWNER_ID)).thenReturn(true);
        when(repository.save(any(OwnerStatement.class))).thenAnswer(call -> call.getArgument(0));

        // A quiet month with a big repair bill.
        OwnerStatementResponse created =
                service.create(request("1000.00", "100.00", "0.00", "0.00", "5000.00"));

        assertThat(created.netPayout()).isEqualByComparingTo("-4100.00");
    }

    @Test
    @DisplayName("create: the statement is written to the audit trail")
    void createIsAudited() {
        when(userService.existsById(OWNER_ID)).thenReturn(true);
        when(repository.save(any(OwnerStatement.class))).thenAnswer(call -> call.getArgument(0));

        service.create(request("10000.00", null, null, null, null));

        verify(auditService).record(eq(OWNER_ID), contains("CREATE"), eq("OwnerStatement"));
    }

    @Test
    @DisplayName("create: refuses a statement for an owner that doesn't exist")
    void createRequiresARealOwner() {
        when(userService.existsById(OWNER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("10000.00", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Owner");

        verify(repository, never()).save(any(OwnerStatement.class));
        // Nothing happened, so nothing is logged.
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("update: the net payout is recalculated from the new amounts")
    void updateRecomputesNetPayout() {
        OwnerStatement existing = new OwnerStatement();
        existing.setId(1L);
        existing.setOwnerId(OWNER_ID);
        existing.setPeriod("2026-08");
        existing.setNetPayout(new BigDecimal("42750.00"));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(userService.existsById(OWNER_ID)).thenReturn(true);
        when(repository.save(any(OwnerStatement.class))).thenAnswer(call -> call.getArgument(0));

        OwnerStatementResponse updated =
                service.update(1L, request("60000.00", "5000.00", "4000.00", "0.00", "0.00"));

        assertThat(updated.netPayout()).isEqualByComparingTo("51000.00");
    }

    @Test
    @DisplayName("getById: unknown id gives a not-found error")
    void getByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(auditService, never()).record(anyLong(), anyString(), anyString());
    }

    // ---------------- the owner's decision (and the payout gate) ----------------

    @Test
    @DisplayName("approve: an issued statement becomes APPROVED, which is what unlocks the payout")
    void approveMarksTheStatementApproved() {
        when(repository.findById(1L)).thenReturn(Optional.of(issued(1L)));
        when(repository.save(any(OwnerStatement.class))).thenAnswer(call -> call.getArgument(0));
        when(userService.getFinanceUsers()).thenReturn(List.of(financeUser(9L)));

        OwnerStatementResponse approved = service.approve(1L, new OwnerStatementDecisionRequest(null));

        assertThat(approved.status()).isEqualTo(StatementStatus.APPROVED);
        assertThat(approved.decidedDate()).isNotNull();
        verify(auditService).record(eq(OWNER_ID), contains("OWNER_APPROVE"), eq("OwnerStatement"));
    }

    @Test
    @DisplayName("approve: Finance is told, so they know the payout can go out")
    void approveNotifiesFinance() {
        when(repository.findById(1L)).thenReturn(Optional.of(issued(1L)));
        when(repository.save(any(OwnerStatement.class))).thenAnswer(call -> call.getArgument(0));
        when(userService.getFinanceUsers()).thenReturn(List.of(financeUser(9L), financeUser(10L)));

        service.approve(1L, new OwnerStatementDecisionRequest(null));

        // Finance is a ROLE, not one person — everyone who could act on it is told.
        verify(notificationClient).notifyUser(eq(9L), contains("APPROVED"), eq("PAYOUT"));
        verify(notificationClient).notifyUser(eq(10L), contains("APPROVED"), eq("PAYOUT"));
    }

    @Test
    @DisplayName("reject: the owner's reason reaches Finance verbatim, so they know what to fix")
    void rejectNotifiesFinanceWithTheReason() {
        when(repository.findById(1L)).thenReturn(Optional.of(issued(1L)));
        when(repository.save(any(OwnerStatement.class))).thenAnswer(call -> call.getArgument(0));
        when(userService.getFinanceUsers()).thenReturn(List.of(financeUser(9L)));

        OwnerStatementResponse rejected = service.reject(
                1L, new OwnerStatementDecisionRequest("Maintenance cost isn't mine"));

        assertThat(rejected.status()).isEqualTo(StatementStatus.REJECTED);
        assertThat(rejected.ownerNote()).isEqualTo("Maintenance cost isn't mine");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationClient).notifyUser(eq(9L), message.capture(), eq("PAYOUT"));
        assertThat(message.getValue()).contains("REJECTED").contains("Maintenance cost isn't mine");
    }

    @Test
    @DisplayName("reject: a reason is required — 'no' with no explanation is useless to Finance")
    void rejectRequiresAReason() {
        when(repository.findById(1L)).thenReturn(Optional.of(issued(1L)));

        assertThatThrownBy(() -> service.reject(1L, new OwnerStatementDecisionRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any(OwnerStatement.class));
        verifyNoInteractions(notificationClient);
    }

    @Test
    @DisplayName("approve: a DRAFT isn't the owner's to answer yet")
    void approveRefusesADraft() {
        OwnerStatement draft = issued(1L);
        draft.setStatus(StatementStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.approve(1L, new OwnerStatementDecisionRequest(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issued");

        verify(repository, never()).save(any(OwnerStatement.class));
    }

    @Test
    @DisplayName("approve: an already-PAID statement can't be re-approved")
    void approveRefusesAPaidStatement() {
        OwnerStatement paid = issued(1L);
        paid.setStatus(StatementStatus.PAID);
        when(repository.findById(1L)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> service.approve(1L, new OwnerStatementDecisionRequest(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("approve: an owner can't sign off SOMEONE ELSE'S statement")
    void approveRefusesAnotherOwnersStatement() {
        when(repository.findById(1L)).thenReturn(Optional.of(issued(1L)));
        // Signed in as a different owner — the URL rule can't catch this, so the
        // service must, or one owner could release another's money.
        when(currentUserService.hasRole(UserRole.OWNER)).thenReturn(true);
        when(currentUserService.currentUserId()).thenReturn(Optional.of(999L));

        assertThatThrownBy(() -> service.approve(1L, new OwnerStatementDecisionRequest(null)))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(repository, never()).save(any(OwnerStatement.class));
    }

    @Test
    @DisplayName("isApproved: only an APPROVED statement opens the payout gate")
    void isApprovedOnlyForApproved() {
        OwnerStatement statement = issued(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(statement));

        // ISSUED — the owner hasn't answered, so no payout.
        assertThat(service.isApproved(1L)).isFalse();

        statement.setStatus(StatementStatus.APPROVED);
        assertThat(service.isApproved(1L)).isTrue();

        statement.setStatus(StatementStatus.REJECTED);
        assertThat(service.isApproved(1L)).isFalse();
    }

    @Test
    @DisplayName("isApproved: a null or unknown id is never approved (fails closed)")
    void isApprovedFailsClosed() {
        assertThat(service.isApproved(null)).isFalse();
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThat(service.isApproved(99L)).isFalse();
    }

    @Test
    @DisplayName("update: re-issuing a rejected statement clears the old decision")
    void updateReissueClearsTheDecision() {
        OwnerStatement rejected = issued(1L);
        rejected.setStatus(StatementStatus.REJECTED);
        rejected.setOwnerNote("Wrong maintenance figure");
        when(repository.findById(1L)).thenReturn(Optional.of(rejected));
        when(userService.existsById(OWNER_ID)).thenReturn(true);
        when(repository.save(any(OwnerStatement.class))).thenAnswer(call -> call.getArgument(0));

        OwnerStatementRequest corrected = new OwnerStatementRequest(OWNER_ID, "2026-08",
                new BigDecimal("50000.00"), null, null, null, null, StatementStatus.ISSUED);
        OwnerStatementResponse reissued = service.update(1L, corrected);

        // Back to awaiting the owner, with the stale rejection wiped — otherwise the
        // statement would stay "rejected" forever and never pay out.
        assertThat(reissued.status()).isEqualTo(StatementStatus.ISSUED);
        assertThat(reissued.ownerNote()).isNull();
        assertThat(reissued.decidedDate()).isNull();
    }
}
