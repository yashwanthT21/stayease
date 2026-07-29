package com.stayease.property.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * Wires a client-side load-balanced RestClient for talking to other services.
 *
 * @LoadBalanced makes Spring Cloud LoadBalancer intercept requests whose host is
 * a service NAME (e.g. "notification-service") and swap in a real instance's
 * host:port pulled from Eureka. That's what lets NotificationClient call
 * http://notification-service/... without hard-coding where it runs.
 *
 * IMPORTANT: the load-balanced builder must NOT be the only / default
 * RestClient.Builder bean. Eureka's own HTTP transport
 * (RestClientDiscoveryClientOptionalArgs) pulls a RestClient.Builder from the
 * context via ObjectProvider#getIfAvailable to talk to the Eureka server. If it
 * grabs the @LoadBalanced builder, the load balancer treats the Eureka host
 * ("localhost") as a service name, finds no instances, and registration fails
 * with "No instances available for localhost". So we expose a plain @Primary
 * builder for infrastructure / default use and keep the load-balanced one
 * separate, selected explicitly via the @LoadBalanced qualifier.
 */

@Configuration
public class RestClientConfig {

    /**
     * Plain, non-load-balanced builder. @Primary so Eureka's transport client and
     * any unqualified injection resolve to this one (never the load-balanced one).
     * Prototype-scoped to mirror Spring Boot's auto-configured builder that this
     * bean replaces, so each consumer gets its own instance.
     */
    @Bean
    @Primary
    @Scope("prototype")
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    RestClient.Builder notificationRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient notificationRestClient(
            @LoadBalanced RestClient.Builder builder) {

        return builder
                .baseUrl("http://notification-service")
                .build();
    }
}

