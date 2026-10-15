package com.financetracker.backend.services;

import com.financetracker.backend.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AuthenticationSessionService {
    private final AuthService auth;
    private final RefreshTokenService sessions;
    @Transactional
    public RefreshTokenService.IssuedSession login(LoginRequest request) {
        AuthResponse response = auth.login(request);
        return sessions.issue(response.getUserId(), response);
    }
    // Preserve the existing explicit-sign-in registration flow; no persistent login on signup.
    public AuthResponse register(RegisterRequest request) { return auth.register(request); }
}
