package com.financetracker.backend.services;

import com.financetracker.backend.dto.AuthResponse;
import com.financetracker.backend.dto.ChangePasswordRequest;
import com.financetracker.backend.dto.UpdateProfileRequest;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.UserRepository;
import com.financetracker.backend.security.JwtService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AuthenticatedUserService authenticatedUserService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public void verifyPassword(Authentication authentication, String password) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        requireCorrectPassword(user, password);
    }

    @Transactional
    public AuthResponse updateProfile(Authentication authentication, UpdateProfileRequest request) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        requireCorrectPassword(user, request.getCurrentPassword());

        String username = request.getUsername().trim();
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByUsernameAndIdNot(username, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        user.setUsername(username);
        user.setEmail(email);
        User savedUser = userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(jwtService.generateToken(savedUser))
                .tokenType("Bearer")
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .build();
    }

    @Transactional
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        requireCorrectPassword(user, request.getCurrentPassword());

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private void requireCorrectPassword(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT).trim();
    }
}
