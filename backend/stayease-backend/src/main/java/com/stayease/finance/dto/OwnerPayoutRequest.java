package com.stayease.finance.dto;

import com.stayease.finance.enums.PayoutStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OwnerPayoutRequest(

        @NotNull(message = "statementId is required")
        Long statementId,

        @NotNull(message = "ownerId is required")
        Long ownerId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
        BigDecimal amount,

        LocalDate paymentDate,

        @Size(max = 120)
        String bankAccountRef,

        PayoutStatus status // optional — defaults to PENDING
) {
}
