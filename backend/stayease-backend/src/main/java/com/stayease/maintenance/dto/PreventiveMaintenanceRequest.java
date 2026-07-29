package com.stayease.maintenance.dto;

import com.stayease.maintenance.enums.PreventiveFrequency;
import com.stayease.maintenance.enums.PreventiveStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PreventiveMaintenanceRequest(

        @NotNull(message = "propertyId is required")
        Long propertyId,

        @NotBlank(message = "taskName is required")
        @Size(max = 150)
        String taskName,

        @NotNull(message = "frequency is required")
        PreventiveFrequency frequency,

        LocalDate nextScheduledDate,

        LocalDate lastCompletedDate,

        PreventiveStatus status // optional — defaults to SCHEDULED
) {
}
