package com.financetracker.backend.dto;

import com.financetracker.backend.entities.RecurringFrequency;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpcomingRecurringTransactionResponse(
        Long recurringTransactionId,
        String description,
        String merchant,
        BigDecimal amount,
        TransactionCategory category,
        TransactionType type,
        RecurringFrequency frequency,
        LocalDate scheduledDate
) {
}
