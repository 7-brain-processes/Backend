package com.classroom.core.service;

import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.LoginRequest;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.exception.DuplicateResourceException;
import com.classroom.core.model.User;
import com.classroom.core.repository.UserRepository;
import com.classroom.core.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .build();

        User saved = userRepository.save(user);
        String token = jwtProvider.generateToken(saved.getId(), saved.getUsername());
        return new AuthResponse(token, UserDto.from(saved));
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String token = jwtProvider.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, UserDto.from(user));
    }
}
