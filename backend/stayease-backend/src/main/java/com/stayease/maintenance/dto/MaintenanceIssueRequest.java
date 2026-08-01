package com.stayease.maintenance.dto;

import com.stayease.maintenance.enums.MaintenanceCategory;
import com.stayease.maintenance.enums.MaintenancePriority;
import com.stayease.maintenance.enums.MaintenanceStatus;
import com.stayease.maintenance.enums.ReportedByType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Incoming JSON for a maintenance issue.
 *
 * reportedDate is server-managed (stamped when the issue is logged), so it's
 * not in the request. resolvedDate is supplied later, when the issue closes.
 */
public record MaintenanceIssueRequest(

        @NotNull(message = "propertyId is required")
        Long propertyId,

        @NotNull(message = "reportedById is required")
        Long reportedById,

        @NotNull(message = "reportedByType is required")
        ReportedByType reportedByType,

        @NotNull(message = "category is required")
        MaintenanceCategory category,

        String description,

        MaintenancePriority priority, // optional — defaults to MEDIUM

        Long assignedContractorId,    // optional, free-form (not a user id)

        LocalDateTime resolvedDate,   // optional — set when resolved

        @DecimalMin(value = "0.0", message = "amountSpent cannot be negative")
        BigDecimal amountSpent,       // optional — repair cost logged on resolution

        MaintenanceStatus status      // optional — defaults to OPEN
) {
}
