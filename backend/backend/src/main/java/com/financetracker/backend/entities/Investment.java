package com.financetracker.backend.entities;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "investments",
        check = {
                @CheckConstraint(name = "ck_investments_shares_positive", constraint = "shares > 0"),
                @CheckConstraint(name = "ck_investments_purchase_price_positive", constraint = "purchase_price > 0")
        },
        indexes = {
                @Index(name = "idx_investments_user_ticker", columnList = "user_id, ticker"),
                @Index(name = "idx_investments_user_purchase_date", columnList = "user_id, purchase_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_investments_user")
    )
    private User user;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(nullable = false, precision = 19, scale = 6)
    @Positive
    private BigDecimal shares;

    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 4)
    @Positive
    private BigDecimal purchasePrice;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    void normalizeInvestment() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (ticker != null) {
            ticker = ticker.trim().toUpperCase(Locale.ROOT);
        }
    }
}
