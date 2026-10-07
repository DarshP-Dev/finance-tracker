package com.financetracker.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** A small event receipt survives notification deletion, so the alert cannot reappear. */
@Entity
@Table(name = "notification_receipts", uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_receipts_user_key", columnNames = {"user_id", "reference_key"}))
@Getter @NoArgsConstructor
public class NotificationReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notification_receipts_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;
    @Column(name = "reference_key", nullable = false, length = 200)
    private String referenceKey;
}
