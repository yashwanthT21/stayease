package com.stayease.notification.dto;

import com.stayease.notification.enums.NotificationCategory;
import com.stayease.notification.enums.NotificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Incoming JSON for a notification. createdDate is server-stamped at creation.
 */
public record NotificationRequest(

        @NotNull(message = "userId is required")
        Long userId,

        @NotBlank(message = "message is required")
        @Size(max = 500)
        String message,

        @NotNull(message = "category is required")
        NotificationCategory category,

        NotificationStatus status // optional — defaults to UNREAD
) {
}
