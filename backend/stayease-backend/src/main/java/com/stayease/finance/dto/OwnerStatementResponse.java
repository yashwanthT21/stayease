package com.stayease.finance.dto;

import com.stayease.finance.enums.StatementStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OwnerStatementResponse(
        Long id,
        Long ownerId,
        String period,
        BigDecimal grossRevenue,
        BigDecimal platformFee,
        BigDecimal managementFee,
        BigDecimal cleaningRevenue,
        BigDecimal maintenanceCost,
        BigDecimal netPayout,
        LocalDateTime generatedDate,
        StatementStatus status,
        /** The owner's approve/reject comment — usually why they rejected it. */
        String ownerNote,
        /** When the owner answered; null while the statement is still awaiting them. */
        LocalDateTime decidedDate
) {
}
