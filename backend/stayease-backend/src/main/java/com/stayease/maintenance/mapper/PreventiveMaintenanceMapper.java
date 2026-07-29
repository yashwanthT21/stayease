package com.stayease.maintenance.mapper;

import com.stayease.maintenance.dto.PreventiveMaintenanceRequest;
import com.stayease.maintenance.dto.PreventiveMaintenanceResponse;
import com.stayease.maintenance.entity.PreventiveMaintenance;
import com.stayease.maintenance.enums.PreventiveStatus;

public final class PreventiveMaintenanceMapper {

    private PreventiveMaintenanceMapper() {
    }

    public static PreventiveMaintenance toEntity(PreventiveMaintenanceRequest request) {
        PreventiveMaintenance p = new PreventiveMaintenance();
        p.setPropertyId(request.propertyId());
        apply(p, request);
        return p;
    }

    public static void updateEntity(PreventiveMaintenance p, PreventiveMaintenanceRequest request) {
        p.setPropertyId(request.propertyId());
        apply(p, request);
    }

    private static void apply(PreventiveMaintenance p, PreventiveMaintenanceRequest request) {
        p.setTaskName(request.taskName());
        p.setFrequency(request.frequency());
        p.setNextScheduledDate(request.nextScheduledDate());
        p.setLastCompletedDate(request.lastCompletedDate());
        p.setStatus(request.status() != null ? request.status() : PreventiveStatus.SCHEDULED);
    }

    public static PreventiveMaintenanceResponse toResponse(PreventiveMaintenance p) {
        return new PreventiveMaintenanceResponse(
                p.getId(),
                p.getPropertyId(),
                p.getTaskName(),
                p.getFrequency(),
                p.getNextScheduledDate(),
                p.getLastCompletedDate(),
                p.getStatus());
    }
}
