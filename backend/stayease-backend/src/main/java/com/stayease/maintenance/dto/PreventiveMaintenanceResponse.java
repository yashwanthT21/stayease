package com.stayease.maintenance.dto;

import com.stayease.maintenance.enums.PreventiveFrequency;
import com.stayease.maintenance.enums.PreventiveStatus;

import java.time.LocalDate;

public record PreventiveMaintenanceResponse(
        Long id,
        Long propertyId,
        String taskName,
        PreventiveFrequency frequency,
        LocalDate nextScheduledDate,
        LocalDate lastCompletedDate,
        PreventiveStatus status
) {
}
