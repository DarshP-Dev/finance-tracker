package com.financetracker.backend.services.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record MarketQuote(String symbol, BigDecimal currentPrice, String currency,
                          BigDecimal previousClose, Instant marketTimestamp,
                          LocalDate sessionDate, boolean confirmedClose) {
    public MarketQuote(String symbol, BigDecimal price, String currency, BigDecimal previousClose, Instant timestamp) {
        this(symbol, price, currency, previousClose, timestamp, null, false);
    }
    public MarketQuote {
        if (sessionDate == null && marketTimestamp != null)
            sessionDate = marketTimestamp.atZone(ZoneId.of("America/New_York")).toLocalDate();
    }
    /** Market chronology outranks retrieval time; an unknown timestamp never upgrades known data. */
    public int compareMarketData(MarketQuote other) {
        if (other == null) return 1;
        int session = compareNullable(sessionDate, other.sessionDate);
        if (session != 0) return session;
        int closing = Boolean.compare(confirmedClose, other.confirmedClose);
        return closing != 0 ? closing : compareNullable(marketTimestamp, other.marketTimestamp);
    }
    private static <T extends Comparable<? super T>> int compareNullable(T left, T right) {
        return left == null ? right == null ? 0 : -1 : right == null ? 1 : left.compareTo(right);
    }
}
