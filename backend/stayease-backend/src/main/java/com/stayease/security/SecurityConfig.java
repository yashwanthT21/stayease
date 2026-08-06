package com.stayease.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The central security configuration.
 *
 * Reads top-to-bottom in filterChain():
 *  - CSRF disabled: we're a stateless token API, not a cookie/session web app.
 *  - authorizeHttpRequests: the RBAC rules. First match wins, so list the
 *    specific rules before the catch-all anyRequest().
 *  - STATELESS sessions: never create an HttpSession; every request must carry
 *    its own JWT.
 *  - our JWT filter runs before Spring's username/password filter so an
 *    incoming token is turned into an authenticated principal first.
 */
@Configuration
public class SecurityConfig {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtService jwtService,
                          CustomUserDetailsService userDetailsService,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthFilter =
                new JwtAuthenticationFilter(jwtService, userDetailsService);

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // public: register & login
                        .requestMatchers("/api/auth/**").permitAll()
                        // user management: admins only
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // owners (and admins) may read the manager list to assign one to a property
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/users/managers")
                        .hasAnyRole("OWNER", "ADMIN")
                        // managers (and admins) may read the housekeeper list to assign a turnover
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/users/housekeepers")
                        .hasAnyRole("PROPERTY_MANAGER", "ADMIN")
                        // finance (and admins) may read the owner list, to pick whose
                        // statement they're building rather than key in a user id
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/users/owners")
                        .hasAnyRole("FINANCE", "ADMIN")
                        // read-only people picker: owners/managers name a person on
                        // their own records (e.g. who reported a maintenance issue)
                        // instead of typing a raw user id
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/users/directory")
                        .hasAnyRole("OWNER", "PROPERTY_MANAGER", "ADMIN")
                        // name+role label lookup for a single id. Any signed-in
                        // caller, because it exposes strictly less than the
                        // directory above and other services need it (over Feign,
                        // forwarding the end user's token) to name a person in a
                        // notification.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/users/*/summary")
                        .authenticated()
                        // all other user management: admins only
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        // audit trail: admins & finance
                        .requestMatchers("/api/audit-logs/**").hasAnyRole("ADMIN", "FINANCE")
                        // financial postings: admins & finance create/update/delete them;
                        // an owner may READ their own statements & payouts (the owner UI
                        // scopes each request to the signed-in owner's id) so they show up
                        // on the owner dashboard's payout-statement tab.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/owner-statements/**", "/api/owner-payouts/**")
                        .hasAnyRole("ADMIN", "FINANCE", "OWNER")
                        // An owner signs off their OWN statement: approving is what
                        // releases the payout, and rejecting sends it back to
                        // Finance, so these two writes belong to the owner rather
                        // than to the people who produced the figures. The service
                        // still checks the statement is theirs and is ISSUED.
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH,
                                "/api/owner-statements/*/approve", "/api/owner-statements/*/reject")
                        .hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/api/owner-statements/**", "/api/owner-payouts/**")
                        .hasAnyRole("ADMIN", "FINANCE")
                        // everything else: any authenticated user

                        /// /// testing ongoing
                        .requestMatchers("/api/check-ins/**")
                        .hasAnyRole("GUEST", "PROPERTY_MANAGER")
                        /// ///

                        .anyRequest().authenticated())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint) // 401
                        .accessDeniedHandler(accessDeniedHandler))           // 403
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt: a slow, salted hash designed for passwords. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the AuthenticationManager. Because we have a UserDetailsService
     * bean (CustomUserDetailsService) and a PasswordEncoder bean, Spring wires
     * them into it automatically, so login can verify email + password.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
