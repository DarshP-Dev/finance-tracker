package com.financetracker.backend.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "refresh_token_sessions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshTokenSession {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "rotated_at") private Instant rotatedAt;
}
