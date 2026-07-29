package com.stayease.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.stayease.notification.enums.NotificationCategory;
import com.stayease.notification.enums.NotificationStatus;

/**
 * A single in-app notification for a user.
 *
 * userId is a plain Long, NOT a JPA relationship: the user lives in the IAM
 * service's database, so across a service boundary we keep only a soft id
 * reference (no cross-database foreign key).
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false)
    private LocalDateTime createdDate;

}
