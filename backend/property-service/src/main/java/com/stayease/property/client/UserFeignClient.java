package com.stayease.property.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Declarative HTTP contract for the IAM module, which still lives in the
 * stayease-backend monolith.
 *
 * ownerId / managerId are soft references here — the users themselves belong to
 * another service's database — so when a notification needs a person's NAME
 * rather than their id we have to ask for it. That is all this client does.
 *
 * IAM is behind JWT authentication, so the caller's token is forwarded on this
 * call (see {@link FeignClientConfig}); a call made outside a web request has no
 * token and simply comes back 401, which {@link UserClient} treats as "name
 * unknown".
 */
@FeignClient(name = "stayease-backend")
public interface UserFeignClient {

    /** GET /api/users/{id}/summary — just enough to name someone in a message. */
    @GetMapping("/api/users/{id}/summary")
    UserSummary getUserSummary(@PathVariable("id") Long id);

    /** Subset of IAM's user record: who this is, for display purposes. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserSummary(Long id, String name, String role) {
    }
}
