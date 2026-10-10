package com.financetracker.backend.services.market;

import java.util.Map;
import java.util.Set;
import java.time.LocalDate;

public interface MarketDataProvider {
    enum Status { AVAILABLE, STALE, DISABLED, UNAVAILABLE, UNKNOWN_SYMBOL, RATE_LIMITED, INVALID_SYMBOL, UNSUPPORTED_CURRENCY, UNSUPPORTED_ASSET }
    record Result(MarketQuote quote, Status status) {
        public static Result unavailable(Status status) { return new Result(null, status); }
    }
    boolean isConfigured();
    Map<String, Result> getQuotes(Set<String> symbols);
    default Map<String, Result> getClosingQuotes(Set<String> symbols, LocalDate sessionDate) { return Map.of(); }
}
