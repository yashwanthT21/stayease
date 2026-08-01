package com.stayease.maintenance.mapper;

import com.stayease.maintenance.dto.MaintenanceIssueRequest;
import com.stayease.maintenance.dto.MaintenanceIssueResponse;
import com.stayease.maintenance.entity.MaintenanceIssue;
import com.stayease.maintenance.enums.MaintenancePriority;
import com.stayease.maintenance.enums.MaintenanceStatus;

import java.time.LocalDateTime;

public final class MaintenanceIssueMapper {

    private MaintenanceIssueMapper() {
    }

    public static MaintenanceIssue toEntity(MaintenanceIssueRequest request) {
        MaintenanceIssue m = new MaintenanceIssue();
        m.setPropertyId(request.propertyId());
        m.setReportedById(request.reportedById());
        m.setReportedByType(request.reportedByType());
        apply(m, request);
        m.setReportedDate(LocalDateTime.now()); // server-stamped at creation
        return m;
    }

    public static void updateEntity(MaintenanceIssue m, MaintenanceIssueRequest request) {
        m.setPropertyId(request.propertyId());
        m.setReportedById(request.reportedById());
        m.setReportedByType(request.reportedByType());
        apply(m, request);
        // reportedDate left unchanged on update
    }

    private static void apply(MaintenanceIssue m, MaintenanceIssueRequest request) {
        m.setCategory(request.category());
        m.setDescription(request.description());
        m.setPriority(request.priority() != null ? request.priority() : MaintenancePriority.MEDIUM);
        m.setAssignedContractorId(request.assignedContractorId());
        m.setResolvedDate(request.resolvedDate());
        m.setAmountSpent(request.amountSpent());
        m.setStatus(request.status() != null ? request.status() : MaintenanceStatus.OPEN);
    }

    public static MaintenanceIssueResponse toResponse(MaintenanceIssue m) {
        return new MaintenanceIssueResponse(
                m.getId(),
                m.getPropertyId(),
                m.getReportedById(),
                m.getReportedByType(),
                m.getCategory(),
                m.getDescription(),
                m.getPriority(),
                m.getAssignedContractorId(),
                m.getReportedDate(),
                m.getResolvedDate(),
                m.getAmountSpent(),
                m.getStatus());
    }
}
