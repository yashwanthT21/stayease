package com.stayease.booking.service;

import com.stayease.booking.dto.GuestProfileRequest;
import com.stayease.booking.dto.GuestProfileResponse;
import com.stayease.booking.entity.GuestProfile;
import com.stayease.booking.mapper.GuestProfileMapper;
import com.stayease.booking.repository.GuestProfileRepository;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.iam.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for guest profiles. Validates that the linked user exists
 * (cross-module call into IAM's UserService).
 */
@Service
@Transactional
public class GuestProfileServiceImpl implements GuestProfileService {

    private final GuestProfileRepository repository;
    private final UserService userService;

    public GuestProfileServiceImpl(GuestProfileRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @Override
    public GuestProfileResponse create(GuestProfileRequest request) {
        ensureUserExists(request.userId());
        return GuestProfileMapper.toResponse(repository.save(GuestProfileMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuestProfileResponse> getAll(Long userId) {
        List<GuestProfile> guests = (userId == null)
                ? repository.findAll()
                : repository.findByUserId(userId);
        return guests.stream().map(GuestProfileMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GuestProfileResponse getById(Long id) {
        return GuestProfileMapper.toResponse(findOrThrow(id));
    }

    @Override
    public GuestProfileResponse update(Long id, GuestProfileRequest request) {
        GuestProfile guest = findOrThrow(id);
        ensureUserExists(request.userId());
        GuestProfileMapper.updateEntity(guest, request);
        return GuestProfileMapper.toResponse(repository.save(guest));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return id != null && repository.existsById(id);
    }

    private GuestProfile findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Guest profile not found with id " + id));
    }

    private void ensureUserExists(Long userId) {
        if (!userService.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id " + userId);
        }
    }
}
