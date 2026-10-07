package com.financetracker.backend.services;

import com.financetracker.backend.entities.TransactionCategory;
import java.time.LocalDate;
import java.util.List;

public final class FinancialNotificationEvents {
    private FinancialNotificationEvents() {}
    public record BudgetImpact(Long userId, TransactionCategory category, LocalDate date) {}
    public record BudgetsChanged(List<BudgetImpact> impacts) {}
    public record RecurringChanged(Long id) {}
    public record RecurringFailed(Long id) {}
    public record CheckUpcoming() {}
}
