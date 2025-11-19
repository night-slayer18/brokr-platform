package io.brokr.security.model;

import io.brokr.core.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class BrokrUserDetails implements UserDetails {

    @Getter
    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;

    public BrokrUserDetails(User user) {
        this.user = user;
        this.authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        // Note: Password hash is required by DaoAuthenticationProvider during authentication.
        // After authentication, JWT tokens are used and password is not stored in security context.
        // The password hash is BCrypt-encrypted and only used for verification during login.
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        // TODO: Implement account expiration if needed (requires accountExpirationDate field in User entity)
        // For now, accounts don't expire
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Account is considered locked if inactive
        // This is consistent with isEnabled() which also checks isActive
        return user.isActive();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // TODO: Implement credential expiration if needed (requires credentialsExpirationDate field in User entity)
        // For now, credentials don't expire
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}