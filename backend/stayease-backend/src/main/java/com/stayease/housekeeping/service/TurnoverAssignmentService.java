package com.stayease.housekeeping.service;

import com.stayease.housekeeping.dto.TurnoverAssignmentRequest;
import com.stayease.housekeeping.dto.TurnoverAssignmentResponse;

import java.util.List;

public interface TurnoverAssignmentService {

    TurnoverAssignmentResponse create(TurnoverAssignmentRequest request);

    List<TurnoverAssignmentResponse> getAll(Long propertyId, Long assignedToId);

    TurnoverAssignmentResponse getById(Long id);

    TurnoverAssignmentResponse update(Long id, TurnoverAssignmentRequest request);

    void delete(Long id);

    /** Existence check used by the TurnoverChecklist sub-module. */
    boolean existsById(Long id);
}
