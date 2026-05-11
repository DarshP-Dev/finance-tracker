package com.financetracker.backend.services;

import com.financetracker.backend.dto.AuthResponse;
import com.financetracker.backend.dto.LoginRequest;
import com.financetracker.backend.dto.RegisterRequest;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.UserRepository;
import com.financetracker.backend.security.JwtService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        String username = request.getUsername().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);
        return buildAuthResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (BadCredentialsException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password", exception);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(jwtService.generateToken(user))
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT).trim();
    }
}
