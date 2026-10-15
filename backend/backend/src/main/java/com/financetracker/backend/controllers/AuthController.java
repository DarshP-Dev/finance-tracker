package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.AuthResponse;
import com.financetracker.backend.dto.LoginRequest;
import com.financetracker.backend.dto.RegisterRequest;
import com.financetracker.backend.services.AuthenticationSessionService;
import com.financetracker.backend.services.RefreshTokenService;
import com.financetracker.backend.security.RefreshCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationSessionService authService;
    private final RefreshTokenService sessions;
    private final RefreshCookie cookie;

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        var issued = authService.login(request);
        cookie.set(servletRequest, servletResponse, issued);
        return ResponseEntity.ok(issued.auth());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            var issued = sessions.rotate(cookie.read(request));
            cookie.set(request, response, issued);
            return ResponseEntity.ok(issued.auth());
        } catch (RefreshTokenService.InvalidSession failure) {
            cookie.clear(request, response);
            throw failure;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(cookie.read(request));
        cookie.clear(request, response);
        return ResponseEntity.noContent().build();
    }
}
