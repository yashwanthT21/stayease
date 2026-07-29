package com.stayease.iam.auth;

import com.stayease.iam.auth.dto.AuthResponse;
import com.stayease.iam.auth.dto.LoginRequest;
import com.stayease.iam.auth.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
