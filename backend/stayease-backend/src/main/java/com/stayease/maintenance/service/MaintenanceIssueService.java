package com.stayease.maintenance.service;

import com.stayease.maintenance.dto.MaintenanceIssueRequest;
import com.stayease.maintenance.dto.MaintenanceIssueResponse;
import com.stayease.maintenance.enums.MaintenanceStatus;

import java.util.List;

public interface MaintenanceIssueService {

    MaintenanceIssueResponse create(MaintenanceIssueRequest request);

    List<MaintenanceIssueResponse> getAll(Long propertyId, MaintenanceStatus status);

    MaintenanceIssueResponse getById(Long id);

    MaintenanceIssueResponse update(Long id, MaintenanceIssueRequest request);

    void delete(Long id);
}
