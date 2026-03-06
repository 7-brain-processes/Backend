package com.classroom.core.service;

import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.LoginRequest;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.repository.UserRepository;
import com.classroom.core.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AuthResponse login(LoginRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
