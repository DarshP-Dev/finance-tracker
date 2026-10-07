package com.financetracker.backend.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Best-effort notifications never roll back a financial change or stop the recurring processor. */
@Component @RequiredArgsConstructor
public class NotificationEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventListener.class);
    private final NotificationRulesService rules;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void budgetsChanged(FinancialNotificationEvents.BudgetsChanged event) { safely(() -> rules.checkBudgets(event)); }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recurringChanged(FinancialNotificationEvents.RecurringChanged event) { safely(() -> rules.checkRecurring(event.id())); }
    @EventListener
    public void recurringFailed(FinancialNotificationEvents.RecurringFailed event) { safely(() -> rules.recurringFailed(event.id())); }
    @EventListener
    public void checkUpcoming(FinancialNotificationEvents.CheckUpcoming event) {
        safely(() -> {
            long afterId = 0;
            while (true) {
                var ids = rules.upcomingIds(afterId);
                if (ids.isEmpty()) break;
                safely(() -> rules.checkUpcomingBatch(ids));
                afterId = ids.getLast();
                if (ids.size() < 250) break;
            }
        });
    }
    private void safely(Runnable action) {
        try { action.run(); }
        catch (RuntimeException exception) {
            LOGGER.warn("In-app notification check failed ({})", exception.getClass().getSimpleName());
        }
    }
}
