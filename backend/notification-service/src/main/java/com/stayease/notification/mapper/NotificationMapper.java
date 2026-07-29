package com.stayease.notification.mapper;

import com.stayease.notification.dto.NotificationRequest;
import com.stayease.notification.dto.NotificationResponse;
import com.stayease.notification.entity.Notification;
import com.stayease.notification.enums.NotificationStatus;

import java.time.LocalDateTime;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static Notification toEntity(NotificationRequest request) {
        Notification n = new Notification();
        n.setUserId(request.userId());
        apply(n, request);
        n.setCreatedDate(LocalDateTime.now()); // server-stamped at creation
        return n;
    }

    public static void updateEntity(Notification n, NotificationRequest request) {
        n.setUserId(request.userId());
        apply(n, request);
        // createdDate left unchanged on update (e.g. when marking READ/DISMISSED)
    }

    private static void apply(Notification n, NotificationRequest request) {
        n.setMessage(request.message());
        n.setCategory(request.category());
        n.setStatus(request.status() != null ? request.status() : NotificationStatus.UNREAD);
    }

    public static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUserId(),
                n.getMessage(),
                n.getCategory(),
                n.getStatus(),
                n.getCreatedDate());
    }
}
