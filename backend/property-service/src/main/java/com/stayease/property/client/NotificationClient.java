package com.stayease.property.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Talks to notification-service through {@link NotificationFeignClient} (a service
 * name resolved via Eureka + load balancer).
 *
 * Every call is BEST-EFFORT: notifications are a non-critical side effect, so a
 * notification-service outage must never fail a property write. We therefore
 * swallow and log any error rather than propagate it. In a fuller design this
 * would instead publish an event to a broker for guaranteed, async delivery.
 *
 * The @FeignClient interface owns the HTTP contract; this class owns the policy
 * (what the message says, and that failures are survivable).
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    /** Notification.message is capped at 500 chars by notification-service. */
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final NotificationFeignClient notificationFeignClient;

    public NotificationClient(NotificationFeignClient notificationFeignClient) {
        this.notificationFeignClient = notificationFeignClient;
    }

    /** Notify a property's owner that their listing was created. */
    public void notifyPropertyCreated(Long ownerId, String propertyTitle) {
        send(ownerId,
                "Your property \"" + propertyTitle + "\" has been created.",
                "property-created");
    }

    /**
     * Notify a property manager that a property has just been put in their care.
     *
     * The owner is named because the manager needs to know WHO handed the property
     * over — that's who they'll be reporting to. When the owner's name couldn't be
     * looked up (IAM unreachable, no token to forward) we still send the
     * notification and just say "the owner": knowing about the assignment matters
     * far more than knowing the name.
     */
    public void notifyManagerAssigned(Long managerId, String propertyTitle, String ownerName) {
        String owner = (ownerName == null || ownerName.isBlank()) ? "the owner" : ownerName;
        send(managerId,
                "You have been assigned to \"" + propertyTitle + "\" by " + owner + ".",
                "manager-assigned");
    }

    /**
     * One POST to notification-service. A null recipient is skipped silently:
     * "nobody to tell" is a normal state (a property need not have a manager), not
     * an error.
     *
     * @param event short label used only in the log line, so a failure says which
     *              notification was lost
     */
    private void send(Long userId, String message, String event) {
        if (userId == null) {
            return;
        }
        String body = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH - 3) + "..."
                : message;
        try {
            // status omitted — notification-service defaults it to UNREAD
            notificationFeignClient.create(new NotificationCreateRequest(userId, body, "PROPERTY", null));
            log.info("Sent {} notification to user {}", event, userId);
        } catch (Exception ex) {
            log.warn("Could not send {} notification to user {}: {}", event, userId, ex.getMessage());
        }
    }
}
