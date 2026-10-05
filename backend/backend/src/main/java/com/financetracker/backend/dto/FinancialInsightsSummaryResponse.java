package com.financetracker.backend.dto;

import java.time.Instant;

public record FinancialInsightsSummaryResponse(String summary, Instant generatedAt,
        int sourceInsightCount, boolean aiGenerated, Status status, int retryAfterSeconds) {
    public enum Status { AVAILABLE, DISABLED, EMPTY, DETERMINISTIC, UNAVAILABLE, COOLDOWN }
}
