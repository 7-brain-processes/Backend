package com.classroom.core.security;

import com.classroom.core.model.User;
import com.classroom.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_returnsUserPrincipal_whenUserExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("johndoe")
                .passwordHash("hashed")
                .createdAt(Instant.now())
                .build();
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("johndoe");

        assertThat(result).isInstanceOf(UserPrincipal.class);
        assertThat(result.getUsername()).isEqualTo("johndoe");
        assertThat(result.getPassword()).isEqualTo("hashed");
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFound_whenUserDoesNotExist() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
