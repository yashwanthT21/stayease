package com.stayease.finance.mapper;

import com.stayease.finance.dto.OwnerStatementRequest;
import com.stayease.finance.dto.OwnerStatementResponse;
import com.stayease.finance.entity.OwnerStatement;
import com.stayease.finance.enums.StatementStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class OwnerStatementMapper {

    private OwnerStatementMapper() {
    }

    public static OwnerStatement toEntity(OwnerStatementRequest request) {
        OwnerStatement s = new OwnerStatement();
        s.setOwnerId(request.ownerId());
        apply(s, request);
        s.setGeneratedDate(LocalDateTime.now()); // server-stamped at creation
        return s;
    }

    /**
     * Finance editing a statement. When they change the figures of one the owner
     * already REJECTED, that is a re-issue: the old decision no longer applies to
     * these numbers, so it is cleared and the statement goes back to awaiting the
     * owner. Without this, a rejected statement could be quietly edited and remain
     * rejected (blocking payout forever) or — worse, if the reverse were done —
     * keep a stale APPROVED against figures the owner never saw.
     */
    public static void updateEntity(OwnerStatement s, OwnerStatementRequest request) {
        s.setOwnerId(request.ownerId());
        boolean wasDecided = s.getStatus() == StatementStatus.APPROVED
                || s.getStatus() == StatementStatus.REJECTED;
        apply(s, request);
        if (wasDecided && s.getStatus() == StatementStatus.ISSUED) {
            s.setOwnerNote(null);
            s.setDecidedDate(null);
        }
        // generatedDate left unchanged on update
    }

    private static void apply(OwnerStatement s, OwnerStatementRequest request) {
        s.setPeriod(request.period());
        s.setGrossRevenue(request.grossRevenue());
        s.setPlatformFee(request.platformFee());
        s.setManagementFee(request.managementFee());
        s.setCleaningRevenue(request.cleaningRevenue());
        s.setMaintenanceCost(request.maintenanceCost());
        s.setStatus(request.status() != null ? request.status() : StatementStatus.DRAFT);
        s.setNetPayout(computeNetPayout(request)); // server-computed
    }

    /** income (gross + cleaning) minus all the deductions; missing values = 0. */
    private static BigDecimal computeNetPayout(OwnerStatementRequest r) {
        return orZero(r.grossRevenue())
                .add(orZero(r.cleaningRevenue()))
                .subtract(orZero(r.platformFee()))
                .subtract(orZero(r.managementFee()))
                .subtract(orZero(r.maintenanceCost()));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public static OwnerStatementResponse toResponse(OwnerStatement s) {
        return new OwnerStatementResponse(
                s.getId(),
                s.getOwnerId(),
                s.getPeriod(),
                s.getGrossRevenue(),
                s.getPlatformFee(),
                s.getManagementFee(),
                s.getCleaningRevenue(),
                s.getMaintenanceCost(),
                s.getNetPayout(),
                s.getGeneratedDate(),
                s.getStatus(),
                s.getOwnerNote(),
                s.getDecidedDate());
    }
}
