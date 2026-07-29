package com.stayease.maintenance.service;

import com.stayease.common.client.PropertyClient;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.maintenance.dto.PreventiveMaintenanceRequest;
import com.stayease.maintenance.dto.PreventiveMaintenanceResponse;
import com.stayease.maintenance.entity.PreventiveMaintenance;
import com.stayease.maintenance.mapper.PreventiveMaintenanceMapper;
import com.stayease.maintenance.repository.PreventiveMaintenanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PreventiveMaintenanceServiceImpl implements PreventiveMaintenanceService {

    private final PreventiveMaintenanceRepository repository;
    private final PropertyClient propertyClient;

    public PreventiveMaintenanceServiceImpl(PreventiveMaintenanceRepository repository,
                                            PropertyClient propertyClient) {
        this.repository = repository;
        this.propertyClient = propertyClient;
    }

    @Override
    public PreventiveMaintenanceResponse create(PreventiveMaintenanceRequest request) {
        ensurePropertyExists(request.propertyId());
        return PreventiveMaintenanceMapper.toResponse(
                repository.save(PreventiveMaintenanceMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PreventiveMaintenanceResponse> getAll(Long propertyId) {
        List<PreventiveMaintenance> list = (propertyId == null)
                ? repository.findAll()
                : repository.findByPropertyId(propertyId);
        return list.stream().map(PreventiveMaintenanceMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PreventiveMaintenanceResponse getById(Long id) {
        return PreventiveMaintenanceMapper.toResponse(findOrThrow(id));
    }

    @Override
    public PreventiveMaintenanceResponse update(Long id, PreventiveMaintenanceRequest request) {
        PreventiveMaintenance entity = findOrThrow(id);
        ensurePropertyExists(request.propertyId());
        PreventiveMaintenanceMapper.updateEntity(entity, request);
        return PreventiveMaintenanceMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private PreventiveMaintenance findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Preventive maintenance task not found with id " + id));
    }

    private void ensurePropertyExists(Long propertyId) {
        if (!propertyClient.existsById(propertyId)) {
            throw new ResourceNotFoundException("Property not found with id " + propertyId);
        }
    }
}
