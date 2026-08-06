package com.stayease.common.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends notifications to notification-service through
 * {@link NotificationFeignClient} (a service name resolved via Eureka + load
 * balancer).
 *
 * Every call is BEST-EFFORT. A notification is a side effect of the business
 * action that triggered it — a guest's booking request must still succeed if
 * notification-service is down or slow, so failures are logged and swallowed
 * rather than propagated (and never roll back the caller's transaction). In a
 * fuller design this would publish an event to a broker for guaranteed, async
 * delivery instead.
 *
 * A null recipient is skipped silently: not every property has a manager
 * assigned, and "nobody to tell" is a normal state, not an error.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final NotificationFeignClient notificationFeignClient;

    public NotificationClient(NotificationFeignClient notificationFeignClient) {
        this.notificationFeignClient = notificationFeignClient;
    }

    /**
     * POST one notification for one user.
     *
     * @param userId   recipient; null is skipped (e.g. property with no manager)
     * @param message  the human-readable text shown in their inbox
     * @param category a NotificationCategory name the notification service knows
     *                 (BOOKING, REVIEW, …) — sent as a String so the two services
     *                 don't have to share a code module
     */
    public void notifyUser(Long userId, String message, String category) {
        if (userId == null) {
            return;
        }
        // Notification.message is capped at 500 chars; trim rather than 400.
        String body = message.length() > 500 ? message.substring(0, 497) + "..." : message;
        try {
            // status omitted — notification-service defaults it to UNREAD
            notificationFeignClient.create(new NotificationCreateRequest(userId, body, category, null));
            log.info("Sent {} notification to user {}", category, userId);
        } catch (Exception ex) {
            log.warn("Could not send {} notification to user {}: {}", category, userId, ex.getMessage());
        }
    }

    /** Outbound payload; mirrors notification-service's NotificationRequest by JSON shape only. */
    public record NotificationCreateRequest(Long userId, String message, String category, String status) {
    }
}
