package com.stayease.notification.enums;

public enum NotificationCategory {
    BOOKING,
    CHECK_IN,
    HOUSEKEEPING,
    MAINTENANCE,
    PAYOUT,
    REVIEW,
    // Property-lifecycle events (e.g. "your listing was created"), raised by the
    // property-service when it calls in to notify an owner.
    PROPERTY
}
