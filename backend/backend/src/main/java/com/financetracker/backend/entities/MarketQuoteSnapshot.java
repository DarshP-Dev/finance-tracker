package com.financetracker.backend.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Public market data shared across users; no holdings, account IDs or credentials. */
@Entity
@Table(name = "market_quote_snapshots")
@Getter @NoArgsConstructor
public class MarketQuoteSnapshot {
    @Id @Column(nullable = false, length = 20)
    private String symbol;
    @Column(nullable = false, precision = 36, scale = 12)
    private BigDecimal price;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "previous_close", precision = 36, scale = 12)
    private BigDecimal previousClose;
    @Column(name = "market_timestamp")
    private Instant marketTimestamp;
    @Column(name = "session_date")
    private LocalDate sessionDate;
    // Nullable for existing rows: legacy quotes were never confirmed as EOD prices.
    @Column(name = "confirmed_close")
    private Boolean confirmedClose;
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
    @Column(nullable = false, length = 32)
    private String source;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
