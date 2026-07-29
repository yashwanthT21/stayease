package com.stayease.iam.auth;

import com.stayease.common.exception.DuplicateResourceException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.iam.auth.dto.AuthResponse;
import com.stayease.iam.auth.dto.LoginRequest;
import com.stayease.iam.auth.dto.RegisterRequest;
import com.stayease.iam.entity.User;
import com.stayease.iam.enums.UserStatus;
import com.stayease.iam.repository.UserRepository;
import com.stayease.security.CustomUserDetailsService;
import com.stayease.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    public AuthServiceImpl(UserRepository userRepository,
                           org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthenticationManager authenticationManager,
                           CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("A user already exists with email " + request.email());
        }
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password())); // hash, never the raw password
        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // Delegates to Spring: loads the user via CustomUserDetailsService and
        // checks the password via BCrypt. Throws BadCredentialsException (an
        // AuthenticationException -> 401 in GlobalExceptionHandler) on mismatch.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email " + request.email()));
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails,
                Map.of("userId", user.getId(), "role", user.getRole().name()));
        return new AuthResponse(token, "Bearer", user.getId(), user.getEmail(), user.getRole());
    }
}
