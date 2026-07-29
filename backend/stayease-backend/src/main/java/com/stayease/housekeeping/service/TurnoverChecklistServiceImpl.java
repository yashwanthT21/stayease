package com.stayease.housekeeping.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.housekeeping.dto.TurnoverChecklistRequest;
import com.stayease.housekeeping.dto.TurnoverChecklistResponse;
import com.stayease.housekeeping.entity.TurnoverChecklist;
import com.stayease.housekeeping.mapper.TurnoverChecklistMapper;
import com.stayease.housekeeping.repository.TurnoverChecklistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TurnoverChecklistServiceImpl implements TurnoverChecklistService {

    private final TurnoverChecklistRepository repository;
    private final TurnoverAssignmentService turnoverAssignmentService;

    public TurnoverChecklistServiceImpl(TurnoverChecklistRepository repository,
                                        TurnoverAssignmentService turnoverAssignmentService) {
        this.repository = repository;
        this.turnoverAssignmentService = turnoverAssignmentService;
    }

    @Override
    public TurnoverChecklistResponse create(TurnoverChecklistRequest request) {
        ensureTurnoverExists(request.turnoverId());
        return TurnoverChecklistMapper.toResponse(
                repository.save(TurnoverChecklistMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoverChecklistResponse> getByTurnover(Long turnoverId) {
        ensureTurnoverExists(turnoverId);
        return repository.findByTurnoverId(turnoverId)
                .stream().map(TurnoverChecklistMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TurnoverChecklistResponse getById(Long id) {
        return TurnoverChecklistMapper.toResponse(findOrThrow(id));
    }

    @Override
    public TurnoverChecklistResponse update(Long id, TurnoverChecklistRequest request) {
        TurnoverChecklist entity = findOrThrow(id);
        ensureTurnoverExists(request.turnoverId());
        TurnoverChecklistMapper.updateEntity(entity, request);
        return TurnoverChecklistMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private TurnoverChecklist findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Checklist item not found with id " + id));
    }

    private void ensureTurnoverExists(Long turnoverId) {
        if (!turnoverAssignmentService.existsById(turnoverId)) {
            throw new ResourceNotFoundException("Turnover assignment not found with id " + turnoverId);
        }
    }
}
