package com.financetracker.backend.services.market;

import java.util.Map;
import java.util.Set;

public interface MarketDataProvider {
    enum Status { AVAILABLE, STALE, DISABLED, UNAVAILABLE, UNKNOWN_SYMBOL, RATE_LIMITED, INVALID_SYMBOL, UNSUPPORTED_CURRENCY, UNSUPPORTED_ASSET }
    record Result(MarketQuote quote, Status status) {
        public static Result unavailable(Status status) { return new Result(null, status); }
    }
    boolean isConfigured();
    Map<String, Result> getQuotes(Set<String> symbols);
}
