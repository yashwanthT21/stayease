package com.stayease.housekeeping.dto;

import com.stayease.housekeeping.enums.ChecklistCategory;
import com.stayease.housekeeping.enums.ChecklistStatus;

public record TurnoverChecklistResponse(
        Long id,
        Long turnoverId,
        String taskName,
        ChecklistCategory category,
        boolean completed,
        String notes,
        ChecklistStatus status
) {
}
