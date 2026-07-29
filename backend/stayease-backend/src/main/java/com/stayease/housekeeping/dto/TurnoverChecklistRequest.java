package com.stayease.housekeeping.dto;

import com.stayease.housekeeping.enums.ChecklistCategory;
import com.stayease.housekeeping.enums.ChecklistStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TurnoverChecklistRequest(

        @NotNull(message = "turnoverId is required")
        Long turnoverId,

        @NotBlank(message = "taskName is required")
        @Size(max = 150)
        String taskName,

        @NotNull(message = "category is required")
        ChecklistCategory category,

        Boolean completed,        // optional — defaults to false

        String notes,

        ChecklistStatus status    // optional — defaults to PENDING
) {
}
