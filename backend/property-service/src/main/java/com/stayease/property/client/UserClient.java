package com.stayease.property.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Looks up a person's display name in IAM.
 *
 * Purely cosmetic, and therefore BEST-EFFORT: it only exists so a notification can
 * say "by Ada Owner" instead of "by user #5". If IAM is down, the token wasn't
 * forwarded, or the id is unknown, we return empty and the caller falls back to a
 * generic wording — a property update must never fail because we couldn't look up
 * a name.
 */
@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final UserFeignClient userFeignClient;

    public UserClient(UserFeignClient userFeignClient) {
        this.userFeignClient = userFeignClient;
    }

    /** The user's name, or empty when it can't be resolved for any reason. */
    public Optional<String> findName(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            UserFeignClient.UserSummary user = userFeignClient.getUserSummary(userId);
            if (user == null || user.name() == null || user.name().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(user.name());
        } catch (Exception ex) {
            log.debug("Could not resolve the name of user {}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }
}
