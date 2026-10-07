package com.financetracker.backend.services;

import static com.financetracker.backend.entities.Notification.Type.*;
import static com.financetracker.backend.entities.Notification.Severity.*;
import com.financetracker.backend.config.NotificationProperties;
import com.financetracker.backend.entities.*;
import com.financetracker.backend.repositories.RecurringTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class NotificationRulesService {
    private final NotificationService notifications;
    private final BudgetService budgets;
    private final RecurringTransactionRepository recurring;
    private final NotificationProperties properties;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkBudgets(FinancialNotificationEvents.BudgetsChanged event) {
        YearMonth current = YearMonth.now(clock);
        event.impacts().stream().distinct().filter(i -> YearMonth.from(i.date()).equals(current)).forEach(i ->
            budgets.getBudgetForNotification(i.userId(), i.category(), current).ifPresent(status -> {
                var budget = status.budget();
                var values = status.values();
                // Compare exact amounts, rather than a rounded display percentage near a threshold.
                boolean exceeded = values.getAmountSpent().compareTo(values.getMonthlyLimit()) >= 0;
                boolean warning = values.getAmountSpent().multiply(BigDecimal.valueOf(100))
                        .compareTo(values.getMonthlyLimit().multiply(properties.warningPercent())) >= 0;
                if (!warning && !exceeded) return;
                String stage = exceeded ? "100" : "warning";
                String message = exceeded
                        ? values.getRemaining().signum() < 0
                            ? "You've exceeded your " + category(budget.getCategory()) + " budget by " + money(values.getRemaining().abs()) + "."
                            : "You've reached your " + category(budget.getCategory()) + " budget limit."
                        : "You've used " + values.getPercentUsed().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            + "% of your " + category(budget.getCategory()) + " budget.";
                notifications.create(budget.getUser(), "budget:" + budget.getId() + ":" + stage + ":" + current,
                        BUDGET, WARNING, exceeded ? "Budget exceeded" : "Budget warning", message, "/budgets", budget.getId());
            }));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkRecurring(Long id) { recurring.findById(id).ifPresent(this::upcoming); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkUpcomingBatch(List<Long> ids) { recurring.findAllById(ids).forEach(this::upcoming); }

    public List<Long> upcomingIds(long afterId) {
        LocalDate today = LocalDate.now(clock);
        return recurring.findNotificationUpcomingIds(today, today.plusDays(properties.upcomingDays()), afterId, PageRequest.of(0, 250));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recurringFailed(Long id) {
        recurring.findById(id).ifPresent(r -> notifications.create(r.getUser(),
                "recurring:" + id + ":" + r.getNextOccurrence() + ":failure", RECURRING, ERROR,
                "Recurring transaction failed", "We couldn't process your " + r.getDescription() + " recurring transaction.",
                "/transactions?tab=recurring", id));
    }

    private void upcoming(RecurringTransaction r) {
        LocalDate today = LocalDate.now(clock), due = r.getNextOccurrence();
        if (!r.isActive() || due.isBefore(today) || due.isAfter(today.plusDays(properties.upcomingDays()))
                || (r.getEndDate() != null && due.isAfter(r.getEndDate()))) return;
        long days = ChronoUnit.DAYS.between(today, due);
        String when = days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
        notifications.create(r.getUser(), "recurring:" + r.getId() + ":" + due + ":upcoming", RECURRING, INFO,
                r.getType() == TransactionType.EXPENSE ? "Upcoming recurring payment" : "Upcoming recurring income",
                r.getDescription() + " of " + money(r.getAmount()) + " is scheduled " + when + ".",
                "/transactions?tab=upcoming", r.getId());
    }

    private static String money(BigDecimal value) { return "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String category(TransactionCategory category) {
        String text = category.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
