package com.stayease.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * StayEase service registry.
 *
 * Every microservice (property-service, notification-service) and the API
 * gateway register themselves here on startup and look each other up by logical
 * name (e.g. "notification-service") instead of a hard-coded host:port. That
 * indirection is what lets the gateway route to lb://property-service and lets
 * property-service call http://notification-service without knowing where it runs.
 *
 * @EnableEurekaServer turns this plain Boot app into the registry itself.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
