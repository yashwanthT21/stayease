package com.stayease.finance.dto;

import com.stayease.finance.enums.PayoutStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OwnerPayoutResponse(
        Long id,
        Long statementId,
        Long ownerId,
        BigDecimal amount,
        LocalDate paymentDate,
        String bankAccountRef,
        PayoutStatus status
) {
}
