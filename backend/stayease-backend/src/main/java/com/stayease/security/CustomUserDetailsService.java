package com.stayease.security;

import com.stayease.iam.entity.User;
import com.stayease.iam.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridges our User entity to Spring Security's UserDetails. Spring calls this
 * to look up an account by its "username" (we use email) during login and on
 * every authenticated request.
 *
 * The role becomes a granted authority named "ROLE_<ROLE>" — that prefix is the
 * convention hasRole("ADMIN") expects (it checks for "ROLE_ADMIN").
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));

        // Legacy users have no password hash — use "" so login simply fails for them.
        String passwordHash = user.getPasswordHash() != null ? user.getPasswordHash() : "";

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(passwordHash)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}
