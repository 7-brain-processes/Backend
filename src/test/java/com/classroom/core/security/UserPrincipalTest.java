package com.classroom.core.security;

import com.classroom.core.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalTest {

    @Test
    void constructsFromUser() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .username("johndoe")
                .passwordHash("hashed")
                .displayName("John")
                .createdAt(Instant.now())
                .build();

        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getId()).isEqualTo(id);
        assertThat(principal.getUsername()).isEqualTo("johndoe");
        assertThat(principal.getPassword()).isEqualTo("hashed");
    }

    @Test
    void authoritiesContainRoleUser() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("johndoe")
                .passwordHash("hashed")
                .createdAt(Instant.now())
                .build();

        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    void accountIsNonExpiredAndEnabled() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("johndoe")
                .passwordHash("hashed")
                .createdAt(Instant.now())
                .build();

        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }
}
