package com.classroom.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtProvider {

    private final String secret;
    private final long expirationMs;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms}") long expirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID userId, String username) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UUID getUserId(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public String getUsername(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean isValid(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
