package com.financetracker.backend.services;

import com.financetracker.backend.dto.AuthResponse;
import com.financetracker.backend.entities.*;
import com.financetracker.backend.repositories.*;
import com.financetracker.backend.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenService {
    private final RefreshTokenSessionRepository sessions;
    private final UserRepository users;
    private final JwtService jwt;
    private final Clock clock;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenSessionRepository sessions, UserRepository users,
            JwtService jwt, Clock clock, @Value("${app.security.refresh.expiration:P30D}") Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) throw new IllegalArgumentException("Refresh lifetime must be positive");
        this.sessions = sessions; this.users = users; this.jwt = jwt; this.clock = clock; this.lifetime = lifetime;
    }

    // Deliberately not a record: default toString must never reveal a raw token.
    public static final class IssuedSession {
        private final String token;
        private final Instant expiresAt;
        private final AuthResponse auth;
        IssuedSession(String token, Instant expiresAt, AuthResponse auth) {
            this.token = token; this.expiresAt = expiresAt; this.auth = auth;
        }
        public String token() { return token; }
        public Instant expiresAt() { return expiresAt; }
        public AuthResponse auth() { return auth; }
    }

    public static final class InvalidSession extends ResponseStatusException {
        public InvalidSession() { super(HttpStatus.UNAUTHORIZED, "Session expired or invalid. Please sign in."); }
    }

    @Transactional
    public IssuedSession issue(Long userId, AuthResponse auth) {
        users.lockForSession(userId).orElseThrow(InvalidSession::new);
        Instant now = clock.instant();
        return create(userId, UUID.randomUUID(), now, now.plus(lifetime), auth);
    }

    // Reuse revocation must COMMIT even though the request returns 401.
    @Transactional(noRollbackFor = InvalidSession.class)
    public IssuedSession rotate(String token) {
        String hash = hashToken(token);
        Long owner = sessions.findOwner(hash).orElseThrow(InvalidSession::new);
        User user = users.lockForSession(owner).orElseThrow(InvalidSession::new);
        RefreshTokenSession session = sessions.findByTokenHash(hash).orElseThrow(InvalidSession::new);
        Instant now = clock.instant();
        if (session.getRotatedAt() != null) {
            sessions.revokeFamily(session.getFamilyId(), now);
            throw new InvalidSession();
        }
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) throw new InvalidSession();
        session.setRevokedAt(now); session.setRotatedAt(now);
        sessions.saveAndFlush(session);
        AuthResponse auth = AuthResponse.builder().accessToken(jwt.generateToken(user)).tokenType("Bearer")
                .userId(user.getId()).username(user.getUsername()).email(user.getEmail()).expiresIn(jwt.expiresIn()).build();
        return create(owner, session.getFamilyId(), now, session.getExpiresAt(), auth);
    }

    @Transactional
    public void logout(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return;
        String hash = hashToken(token);
        sessions.findOwner(hash).ifPresent(owner -> {
            users.lockForSession(owner).ifPresent(user -> sessions.findByTokenHash(hash)
                    .ifPresent(session -> sessions.revokeFamily(session.getFamilyId(), clock.instant())));
        });
    }

    @Transactional
    public void revokeUser(Long userId) {
        users.lockForSession(userId).orElseThrow(InvalidSession::new);
        sessions.revokeUser(userId, clock.instant());
    }

    private IssuedSession create(Long owner, UUID family, Instant now, Instant expiry, AuthResponse auth) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.saveAndFlush(RefreshTokenSession.builder().id(UUID.randomUUID()).userId(owner).familyId(family)
                .tokenHash(hashToken(token)).createdAt(now).expiresAt(expiry).build());
        return new IssuedSession(token, expiry, auth);
    }

    static String hashToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new InvalidSession();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
