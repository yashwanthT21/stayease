package com.stayease.notification.dto;

import com.stayease.notification.enums.NotificationCategory;
import com.stayease.notification.enums.NotificationStatus;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long userId,
        String message,
        NotificationCategory category,
        NotificationStatus status,
        LocalDateTime createdDate
) {
}
