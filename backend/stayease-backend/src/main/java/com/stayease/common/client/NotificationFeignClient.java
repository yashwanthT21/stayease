package com.stayease.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative HTTP contract for notification-service.
 *
 * "notification-service" is its Eureka registration name; Feign + Spring Cloud
 * LoadBalancer turn it into a live instance's address. The best-effort policy
 * (swallow failures so a booking still succeeds) is applied one layer up, in
 * {@link NotificationClient} — this interface just fails loudly.
 *
 * To point at a fixed host instead of discovery, set
 * spring.cloud.openfeign.client.config.notification-service.url.
 */
@FeignClient(name = "notification-service")
public interface NotificationFeignClient {

    /** POST /api/notifications — creates one notification for one user. */
    @PostMapping("/api/notifications")
    void create(@RequestBody NotificationClient.NotificationCreateRequest request);
}
