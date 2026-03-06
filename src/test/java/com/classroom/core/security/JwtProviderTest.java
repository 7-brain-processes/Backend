package com.classroom.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = "Y2xhc3Nyb29tLWFwcC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhtYWMtc2hhMjU2";
    private static final long EXPIRATION_MS = 86_400_000;

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_returnsNonBlankString() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.generateToken(userId, "testuser");
        assertThat(token).isNotBlank();
    }

    @Test
    void getUserId_returnsIdFromToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.generateToken(userId, "testuser");
        assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
    }

    @Test
    void getUsername_returnsUsernameFromToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.generateToken(userId, "testuser");
        assertThat(jwtProvider.getUsername(token)).isEqualTo("testuser");
    }

    @Test
    void isValid_returnsTrueForValidToken() {
        String token = jwtProvider.generateToken(UUID.randomUUID(), "testuser");
        assertThat(jwtProvider.isValid(token)).isTrue();
    }

    @Test
    void isValid_returnsFalseForGarbageString() {
        assertThat(jwtProvider.isValid("not.a.jwt")).isFalse();
    }

    @Test
    void isValid_returnsFalseForTokenSignedWithDifferentSecret() {
        JwtProvider otherProvider = new JwtProvider(
                "YW5vdGhlci1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhtYWMtc2hhMjU2LXRlc3Q=",
                EXPIRATION_MS
        );
        String token = otherProvider.generateToken(UUID.randomUUID(), "testuser");
        assertThat(jwtProvider.isValid(token)).isFalse();
    }

    @Test
    void isValid_returnsFalseForExpiredToken() {
        JwtProvider expiredProvider = new JwtProvider(SECRET, -1000);
        String token = expiredProvider.generateToken(UUID.randomUUID(), "testuser");
        assertThat(jwtProvider.isValid(token)).isFalse();
    }

    @Test
    void differentUsers_produceDifferentTokens() {
        String token1 = jwtProvider.generateToken(UUID.randomUUID(), "user1");
        String token2 = jwtProvider.generateToken(UUID.randomUUID(), "user2");
        assertThat(token1).isNotEqualTo(token2);
    }
}
