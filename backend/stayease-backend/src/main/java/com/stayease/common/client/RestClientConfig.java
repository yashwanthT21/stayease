package com.stayease.common.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * Wires a client-side load-balanced RestClient for calling the extracted
 * property-service.
 *
 * @LoadBalanced makes Spring Cloud LoadBalancer swap a service NAME
 * ("property-service") for a real instance's host:port pulled from Eureka — so
 * PropertyClient can call http://property-service/... without hard-coding where
 * it runs.
 *
 * IMPORTANT: the load-balanced builder must NOT be the only / default
 * RestClient.Builder bean. Eureka's own HTTP transport pulls a RestClient.Builder
 * from the context (via ObjectProvider#getIfAvailable) to reach the Eureka
 * server; if it grabs the @LoadBalanced one, the Eureka host ("localhost") is
 * treated as a service name and registration fails with "No instances available
 * for localhost". We therefore expose a plain @Primary builder for
 * infrastructure / default use and keep the load-balanced one separate, selected
 * via the @LoadBalanced qualifier.
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
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient propertyRestClient(@LoadBalanced RestClient.Builder builder,
                                         @Value("${services.property.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
