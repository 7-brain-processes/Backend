package com.classroom.core.service;

import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.LoginRequest;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.exception.DuplicateResourceException;
import com.classroom.core.model.User;
import com.classroom.core.repository.UserRepository;
import com.classroom.core.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_createsUserAndReturnsTokenAndUserDto() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("johndoe");
        request.setPassword("password123");
        request.setDisplayName("John Doe");

        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");

        UUID userId = UUID.randomUUID();
        User savedUser = User.builder()
                .id(userId)
                .username("johndoe")
                .passwordHash("hashed_password")
                .displayName("John Doe")
                .createdAt(Instant.now())
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtProvider.generateToken(userId, "johndoe")).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getUsername()).isEqualTo("johndoe");
        assertThat(response.getUser().getDisplayName()).isEqualTo("John Doe");
        assertThat(response.getUser().getId()).isEqualTo(userId);
    }

    @Test
    void register_hashesPasswordBeforeSaving() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("johndoe");
        request.setPassword("password123");

        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt_hash");

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .username("johndoe")
                .passwordHash("bcrypt_hash")
                .createdAt(Instant.now())
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtProvider.generateToken(any(), anyString())).thenReturn("token");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("bcrypt_hash");
    }

    @Test
    void register_throwsWhenUsernameAlreadyTaken() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("johndoe");
        request.setPassword("password123");

        when(userRepository.existsByUsername("johndoe")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_authenticatesAndReturnsTokenAndUserDto() {
        LoginRequest request = new LoginRequest();
        request.setUsername("johndoe");
        request.setPassword("password123");

        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("johndoe")
                .passwordHash("hashed")
                .displayName("John Doe")
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));
        when(jwtProvider.generateToken(userId, "johndoe")).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        verify(authenticationManager).authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        );
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getUsername()).isEqualTo("johndoe");
    }

    @Test
    void login_throwsWhenCredentialsAreInvalid() {
        LoginRequest request = new LoginRequest();
        request.setUsername("johndoe");
        request.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_throwsWhenUserNotFoundAfterAuth() {
        LoginRequest request = new LoginRequest();
        request.setUsername("ghost");
        request.setPassword("password123");

        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
