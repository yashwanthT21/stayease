package com.stayease.security;

import com.stayease.iam.entity.User;
import com.stayease.iam.enums.UserRole;
import com.stayease.iam.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Answers "who is making this request?" for business rules that need it.
 *
 * The JWT filter puts the user's EMAIL in the security context as the principal
 * name (see CustomUserDetailsService), which is enough to authenticate but not to
 * compare against a record's ownerId. This resolves that email to the User so a
 * service can enforce per-record ownership — the half of authorization that
 * SecurityConfig's URL patterns can't express.
 *
 * Everything returns Optional.empty() when there is no authenticated caller (a
 * scheduled job, a test), so callers decide what that means rather than getting an
 * exception from deep inside a rule.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The signed-in user, or empty when the request isn't authenticated. */
    @Transactional(readOnly = true)
    public Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(auth.getName());
    }

    /** The signed-in user's id, or empty when unauthenticated. */
    @Transactional(readOnly = true)
    public Optional<Long> currentUserId() {
        return currentUser().map(User::getId);
    }

    /** True when the caller is signed in with exactly this role. */
    @Transactional(readOnly = true)
    public boolean hasRole(UserRole role) {
        return currentUser().map(u -> u.getRole() == role).orElse(false);
    }
}
