package com.stayease.property;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Property microservice.
 *
 * Owns three tables in its own database — properties, availability_calendars,
 * pricing_rules — which are a single bounded context (a property and its per-date
 * availability and pricing rules always change together). On creating a property
 * it calls notification-service (discovered via Eureka) to alert the owner, but
 * that call is best-effort and never blocks the write.
 */
@SpringBootApplication

public class PropertyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertyServiceApplication.class, args);
    }
}
