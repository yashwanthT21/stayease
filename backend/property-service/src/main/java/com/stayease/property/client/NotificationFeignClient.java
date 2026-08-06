package com.stayease.property.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative HTTP contract for notification-service.
 *
 * "notification-service" is the name it registers under in Eureka, not a host:
 * Feign hands it to Spring Cloud LoadBalancer, which swaps in a live instance's
 * host:port. The best-effort policy lives in {@link NotificationClient}; this
 * interface fails loudly so that wrapper can decide what to do.
 */
@FeignClient(name = "notification-service")
public interface NotificationFeignClient {

    /** POST /api/notifications — creates one notification for one user. */
    @PostMapping("/api/notifications")
    void create(@RequestBody NotificationCreateRequest request);
}
