package com.stayease.housekeeping.service;

import com.stayease.housekeeping.dto.TurnoverAssignmentRequest;
import com.stayease.housekeeping.dto.TurnoverAssignmentResponse;
import com.stayease.housekeeping.enums.HousekeeperStatus;
import com.stayease.housekeeping.enums.TurnoverStatus;

import java.util.List;

public interface TurnoverAssignmentService {

    TurnoverAssignmentResponse create(TurnoverAssignmentRequest request);

    List<TurnoverAssignmentResponse> getAll(Long propertyId, Long assignedToId);

    TurnoverAssignmentResponse getById(Long id);

    TurnoverAssignmentResponse update(Long id, TurnoverAssignmentRequest request);

    /** Housekeeper sets their own completion state (PENDING/COMPLETED). */
    TurnoverAssignmentResponse setHousekeeperStatus(Long id, HousekeeperStatus status);

    /** Manager sets the overall status — only allowed once the housekeeper is COMPLETED. */
    TurnoverAssignmentResponse setManagerStatus(Long id, TurnoverStatus status);

    void delete(Long id);

    /** Existence check used by the TurnoverChecklist sub-module. */
    boolean existsById(Long id);
}
