package com.stayease.property;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Property microservice.
 *
 * Owns three tables in its own database — properties, availability_calendars and
 * pricing_rules — which are a single bounded context (a property and its per-date
 * availability always change together). The pricing_rules table has no API of its
 * own: nothing ever consumed it, so its controller/service were removed, and the
 * entity survives only so deleting a property can clean up its old rows.
 *
 * Two outbound calls, both declared as @FeignClient interfaces in
 * com.stayease.property.client and both best-effort:
 *  - notification-service, to tell an owner their listing was created and to tell
 *    a manager they were assigned to a property;
 *  - stayease-backend (IAM), to put the owner's NAME in that manager's message.
 * Neither ever blocks the write.
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.stayease.property.client")
public class PropertyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertyServiceApplication.class, args);
    }
}
