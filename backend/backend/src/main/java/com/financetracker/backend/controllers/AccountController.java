package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.AuthResponse;
import com.financetracker.backend.dto.ChangePasswordRequest;
import com.financetracker.backend.dto.PasswordVerificationResponse;
import com.financetracker.backend.dto.UpdateProfileRequest;
import com.financetracker.backend.dto.VerifyPasswordRequest;
import com.financetracker.backend.services.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/verify-password")
    public ResponseEntity<PasswordVerificationResponse> verifyPassword(
            Authentication authentication,
            @Valid @RequestBody VerifyPasswordRequest request
    ) {
        accountService.verifyPassword(authentication, request.getPassword());
        return ResponseEntity.ok(PasswordVerificationResponse.builder().valid(true).build());
    }

    @PatchMapping("/profile")
    public ResponseEntity<AuthResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(accountService.updateProfile(authentication, request));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        accountService.changePassword(authentication, request);
        return ResponseEntity.noContent().build();
    }
}
