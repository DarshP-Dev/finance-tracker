package com.financetracker.backend.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at, id"),
        @Index(name = "idx_notifications_user_read", columnList = "user_id, is_read")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Notification {
    public enum Type { BUDGET, RECURRING, SYSTEM }
    public enum Severity { INFO, WARNING, ERROR }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Type type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Severity severity;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false, length = 1000)
    private String message;
    @Column(name = "is_read", nullable = false)
    private boolean read;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "target_path", nullable = false, length = 100)
    private String targetPath;
    @Column(name = "related_entity_id")
    private Long relatedEntityId;
}
