package com.financetracker.backend.dto;

import com.financetracker.backend.entities.TransactionCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinancialInsightResponse(
        String key, Type type, Severity severity, String title, String message,
        BigDecimal metricValue, BigDecimal comparisonValue, TransactionCategory category,
        LocalDate from, LocalDate to, Instant generatedAt
) {
    public enum Type { SPENDING, BUDGET, SAVINGS, RECURRING, FORECAST, INCOME, INVESTMENT }
    public enum Severity { INFO, POSITIVE, WARNING }
}
