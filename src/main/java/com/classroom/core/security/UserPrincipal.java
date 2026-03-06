package com.classroom.core.security;

import com.classroom.core.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public UUID getId() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String getUsername() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String getPassword() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isAccountNonExpired() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isAccountNonLocked() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isCredentialsNonExpired() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isEnabled() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
