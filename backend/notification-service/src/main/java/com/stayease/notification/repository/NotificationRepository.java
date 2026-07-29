package com.stayease.notification.repository;

import com.stayease.notification.entity.Notification;
import com.stayease.notification.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserId(Long userId);

    List<Notification> findByUserIdAndStatus(Long userId, NotificationStatus status);

    // Added so a status-only filter (?status=UNREAD, no userId) can be honoured.
    // Previously that case fell through to findAll() and ignored the status.
    List<Notification> findByStatus(NotificationStatus status);
}
