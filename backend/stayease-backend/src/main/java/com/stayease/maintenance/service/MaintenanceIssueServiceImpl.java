package com.stayease.maintenance.service;

import com.stayease.common.client.PropertyClient;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.iam.service.UserService;
import com.stayease.maintenance.dto.MaintenanceIssueRequest;
import com.stayease.maintenance.dto.MaintenanceIssueResponse;
import com.stayease.maintenance.entity.MaintenanceIssue;
import com.stayease.maintenance.enums.MaintenanceStatus;
import com.stayease.maintenance.mapper.MaintenanceIssueMapper;
import com.stayease.maintenance.repository.MaintenanceIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class MaintenanceIssueServiceImpl implements MaintenanceIssueService {

    private final MaintenanceIssueRepository repository;
    private final PropertyClient propertyClient;
    private final UserService userService;

    public MaintenanceIssueServiceImpl(MaintenanceIssueRepository repository,
                                       PropertyClient propertyClient,
                                       UserService userService) {
        this.repository = repository;
        this.propertyClient = propertyClient;
        this.userService = userService;
    }

    @Override
    public MaintenanceIssueResponse create(MaintenanceIssueRequest request) {
        validateReferences(request);
        return MaintenanceIssueMapper.toResponse(
                repository.save(MaintenanceIssueMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceIssueResponse> getAll(Long propertyId, MaintenanceStatus status) {
        List<MaintenanceIssue> list;
        if (propertyId != null) {
            list = repository.findByPropertyId(propertyId);
        } else if (status != null) {
            list = repository.findByStatus(status);
        } else {
            list = repository.findAll();
        }
        return list.stream().map(MaintenanceIssueMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceIssueResponse getById(Long id) {
        return MaintenanceIssueMapper.toResponse(findOrThrow(id));
    }

    @Override
    public MaintenanceIssueResponse update(Long id, MaintenanceIssueRequest request) {
        MaintenanceIssue entity = findOrThrow(id);
        validateReferences(request);
        MaintenanceIssueMapper.updateEntity(entity, request);
        return MaintenanceIssueMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private MaintenanceIssue findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Maintenance issue not found with id " + id));
    }

    private void validateReferences(MaintenanceIssueRequest request) {
        if (!propertyClient.existsById(request.propertyId())) {
            throw new ResourceNotFoundException("Property not found with id " + request.propertyId());
        }
        if (!userService.existsById(request.reportedById())) {
            throw new ResourceNotFoundException("Reporter (user) not found with id " + request.reportedById());
        }
    }
}
