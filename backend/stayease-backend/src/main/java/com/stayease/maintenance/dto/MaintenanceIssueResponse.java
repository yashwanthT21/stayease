package com.stayease.maintenance.dto;

import com.stayease.maintenance.enums.MaintenanceCategory;
import com.stayease.maintenance.enums.MaintenancePriority;
import com.stayease.maintenance.enums.MaintenanceStatus;
import com.stayease.maintenance.enums.ReportedByType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MaintenanceIssueResponse(
        Long id,
        Long propertyId,
        Long reportedById,
        ReportedByType reportedByType,
        MaintenanceCategory category,
        String description,
        MaintenancePriority priority,
        Long assignedContractorId,
        LocalDateTime reportedDate,
        LocalDateTime resolvedDate,
        BigDecimal amountSpent,
        MaintenanceStatus status
) {
}
