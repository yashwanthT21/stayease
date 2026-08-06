package com.stayease;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @EnableFeignClients scans for the @FeignClient interfaces in
 * com.stayease.common.client and creates a proxy bean for each, so the outbound
 * calls to property-service and notification-service are declared rather than
 * hand-written.
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.stayease.common.client")
public class StayEaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(StayEaseApplication.class, args);
    }
}
