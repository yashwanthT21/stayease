package com.stayease.maintenance.service;

import com.stayease.maintenance.dto.PreventiveMaintenanceRequest;
import com.stayease.maintenance.dto.PreventiveMaintenanceResponse;

import java.util.List;

public interface PreventiveMaintenanceService {

    PreventiveMaintenanceResponse create(PreventiveMaintenanceRequest request);

    List<PreventiveMaintenanceResponse> getAll(Long propertyId);

    PreventiveMaintenanceResponse getById(Long id);

    PreventiveMaintenanceResponse update(Long id, PreventiveMaintenanceRequest request);

    void delete(Long id);
}
