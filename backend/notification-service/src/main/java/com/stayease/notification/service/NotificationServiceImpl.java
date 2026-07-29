package com.stayease.notification.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.notification.dto.NotificationRequest;
import com.stayease.notification.dto.NotificationResponse;
import com.stayease.notification.entity.Notification;
import com.stayease.notification.enums.NotificationStatus;
import com.stayease.notification.mapper.NotificationMapper;
import com.stayease.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for notifications.
 *
 * As an extracted microservice this no longer depends on IAM's UserService: the
 * user lives in another service's database, so we treat userId as a soft
 * reference. The gateway has already authenticated the caller; a full system
 * would resolve/validate the user asynchronously (event) or via the IAM service
 * if a hard check were required — we do not couple the write path to it.
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;

    public NotificationServiceImpl(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public NotificationResponse create(NotificationRequest request) {
        return NotificationMapper.toResponse(repository.save(NotificationMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getAll(Long userId, NotificationStatus status) {
        List<Notification> list;
        if (userId != null && status != null) {
            list = repository.findByUserIdAndStatus(userId, status);
        } else if (userId != null) {
            list = repository.findByUserId(userId);
        } else if (status != null) {
            // Bug fix: a status-only filter used to fall through to findAll() and
            // silently ignore the status. Now it filters by status as intended.
            list = repository.findByStatus(status);
        } else {
            list = repository.findAll();
        }
        return list.stream().map(NotificationMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getById(Long id) {
        return NotificationMapper.toResponse(findOrThrow(id));
    }

    @Override
    public NotificationResponse update(Long id, NotificationRequest request) {
        Notification entity = findOrThrow(id);
        NotificationMapper.updateEntity(entity, request);
        return NotificationMapper.toResponse(repository.save(entity));
    }

    @Override
    public NotificationResponse markAsRead(Long id) {
        return transition(id, NotificationStatus.READ);
    }

    @Override
    public NotificationResponse dismiss(Long id) {
        return transition(id, NotificationStatus.DISMISSED);
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    /** Shared status-only transition used by markAsRead / dismiss. Idempotent. */
    private NotificationResponse transition(Long id, NotificationStatus newStatus) {
        Notification entity = findOrThrow(id);
        entity.setStatus(newStatus);
        return NotificationMapper.toResponse(repository.save(entity));
    }

    private Notification findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found with id " + id));
    }
}
