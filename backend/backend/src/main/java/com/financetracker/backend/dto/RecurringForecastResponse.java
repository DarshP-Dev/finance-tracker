package com.financetracker.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringForecastResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal expectedIncome,
        BigDecimal expectedExpenses,
        BigDecimal netCashFlow,
        int incomeOccurrenceCount,
        int expenseOccurrenceCount
) {
}
