package com.financetracker.backend.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Bind an access token to the immutable account ID as well as its email. */
public final class AccountPrincipal extends org.springframework.security.core.userdetails.User {
    private final Long userId;
    public AccountPrincipal(Long userId, String email, String password) {
        super(email, password, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.userId = userId;
    }
    public Long userId() { return userId; }
}
