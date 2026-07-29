package com.stayease.property.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to notification-service over HTTP (resolved via Eureka + load balancer).
 *
 * The call is BEST-EFFORT: notifications are a non-critical side effect, so a
 * notification-service outage must never fail a property creation. We therefore
 * swallow and log any error rather than propagate it. In a fuller design this
 * would instead publish an event to a broker for guaranteed, async delivery.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient notificationRestClient;
    

    public NotificationClient(RestClient notificationRestClient) {
        this.notificationRestClient = notificationRestClient;
    }

    

    /** Notify a property's owner that their listing was created. */
    public void notifyPropertyCreated(Long ownerId, String propertyTitle) {
        NotificationCreateRequest body = new NotificationCreateRequest(
                ownerId,
                "Your property \"" + propertyTitle + "\" has been created.",
                "PROPERTY",
                null); // let notification-service default the status to UNREAD
        try {
            notificationRestClient.post()
                    .uri("/api/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent property-created notification to user {}", ownerId);
        } catch (Exception ex) {
            log.warn("Could not send property-created notification to user {}: {}",
                    ownerId, ex.getMessage());
        }
    }

    
}
