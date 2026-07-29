package com.stayease.finance.dto;

import com.stayease.finance.enums.StatementStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Incoming JSON for an owner statement.
 *
 * netPayout is NOT here — the server computes it:
 *   netPayout = grossRevenue + cleaningRevenue
 *               - platformFee - managementFee - maintenanceCost
 * generatedDate is also server-stamped at creation.
 */
public record OwnerStatementRequest(

        @NotNull(message = "ownerId is required")
        Long ownerId,

        @NotBlank(message = "period is required")
        @Size(max = 20)
        String period, // e.g. "2026-06" or "2026-Q2"

        @DecimalMin(value = "0.0", message = "grossRevenue cannot be negative")
        BigDecimal grossRevenue,

        @DecimalMin(value = "0.0", message = "platformFee cannot be negative")
        BigDecimal platformFee,

        @DecimalMin(value = "0.0", message = "managementFee cannot be negative")
        BigDecimal managementFee,

        @DecimalMin(value = "0.0", message = "cleaningRevenue cannot be negative")
        BigDecimal cleaningRevenue,

        @DecimalMin(value = "0.0", message = "maintenanceCost cannot be negative")
        BigDecimal maintenanceCost,

        StatementStatus status // optional — defaults to DRAFT
) {
}
