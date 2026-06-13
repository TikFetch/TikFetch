package dev.despical.tikfetch.security;

import dev.despical.tikfetch.entity.Admin;

import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AdminPrincipal(Admin admin) implements UserDetails {

    @Override
    @NullMarked
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Override
    public String getPassword() {
        return admin.getPasswordHash();
    }

    @Override
    @NullMarked
    public String getUsername() {
        return admin.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return admin.isEnabled();
    }
}
