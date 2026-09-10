package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financetracker.backend.dto.AuthResponse;
import com.financetracker.backend.dto.ChangePasswordRequest;
import com.financetracker.backend.dto.UpdateProfileRequest;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.UserRepository;
import com.financetracker.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccountServiceTests {

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private Authentication authentication;

    private AccountService accountService;
    private User user;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(authenticatedUserService, userRepository, passwordEncoder, jwtService);
        user = User.builder()
                .id(7L)
                .username("Old Name")
                .email("old@example.com")
                .password("encoded-current-password")
                .build();

        when(authenticatedUserService.getCurrentUser(authentication)).thenReturn(user);
    }

    @Test
    void verifiesTheCurrentPassword() {
        when(passwordEncoder.matches("correct-password", user.getPassword())).thenReturn(true);

        accountService.verifyPassword(authentication, "correct-password");

        verify(passwordEncoder).matches("correct-password", "encoded-current-password");
    }

    @Test
    void rejectsAnIncorrectCurrentPassword() {
        when(passwordEncoder.matches("wrong-password", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> accountService.verifyPassword(authentication, "wrong-password"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("Current password is incorrect");
                });
    }

    @Test
    void updatesTheProfileAndReturnsARefreshedToken() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .username("  New Name  ")
                .email("  NEW@EXAMPLE.COM  ")
                .currentPassword("correct-password")
                .build();
        when(passwordEncoder.matches("correct-password", user.getPassword())).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("refreshed-token");

        AuthResponse response = accountService.updateProfile(authentication, request);

        assertThat(user.getUsername()).isEqualTo("New Name");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getAccessToken()).isEqualTo("refreshed-token");
        assertThat(response.getUsername()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        verify(userRepository).existsByUsernameAndIdNot("New Name", 7L);
        verify(userRepository).existsByEmailAndIdNot("new@example.com", 7L);
    }

    @Test
    void changesThePasswordAfterVerifyingTheCurrentPassword() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("correct-password")
                .newPassword("a-new-secure-password")
                .build();
        when(passwordEncoder.matches("correct-password", user.getPassword())).thenReturn(true);
        when(passwordEncoder.matches("a-new-secure-password", user.getPassword())).thenReturn(false);
        when(passwordEncoder.encode("a-new-secure-password")).thenReturn("encoded-new-password");

        accountService.changePassword(authentication, request);

        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        verify(userRepository).save(user);
    }
}
