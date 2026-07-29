package com.stayease.notification.service;

import com.stayease.notification.dto.NotificationRequest;
import com.stayease.notification.dto.NotificationResponse;
import com.stayease.notification.enums.NotificationStatus;

import java.util.List;

public interface NotificationService {

    NotificationResponse create(NotificationRequest request);

    List<NotificationResponse> getAll(Long userId, NotificationStatus status);

    NotificationResponse getById(Long id);

    NotificationResponse update(Long id, NotificationRequest request);

    /** Mark a single notification READ (idempotent). The real-world action a
     *  user takes far more often than a full update. */
    NotificationResponse markAsRead(Long id);

    /** Dismiss a single notification (idempotent). */
    NotificationResponse dismiss(Long id);

    void delete(Long id);
}
