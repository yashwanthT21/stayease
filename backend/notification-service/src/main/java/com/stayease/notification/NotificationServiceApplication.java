package com.stayease.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification microservice.
 *
 * A pure leaf service: it owns the `notifications` table in its own database and
 * depends on no other service. Other services (e.g. property-service) call IN to
 * create notifications; this service never calls out. That independence is what
 * makes it safe to deploy, scale, and fail on its own.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
