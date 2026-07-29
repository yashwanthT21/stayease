package com.stayease.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * StayEase API gateway — the single front door for the extracted microservices.
 *
 * Responsibilities:
 *  - Route by path to property-service / notification-service (see application.yml),
 *    resolving lb://<service> through Eureka + Spring Cloud LoadBalancer.
 *  - Authenticate every protected request once, at the edge, by verifying the JWT
 *    the IAM module issued (JwtAuthenticationFilter), then forward the caller's
 *    identity to downstream services as X-User-* headers.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
