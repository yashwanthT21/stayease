package com.stayease.property.client;

/**
 * Outbound payload for POST notification-service /api/notifications.
 *
 * This mirrors notification-service's NotificationRequest by JSON shape only —
 * we deliberately DON'T share a code module between services (that would couple
 * their release cycles). category is sent as a String; notification-service maps
 * it to its own enum, and "PROPERTY" is a value it understands.
 */
public record NotificationCreateRequest(
        Long userId,
        String message,
        String category,
        String status
) {
}
