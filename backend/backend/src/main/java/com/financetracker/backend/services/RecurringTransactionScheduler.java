package com.financetracker.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recurring.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RecurringTransactionScheduler {

    private final RecurringTransactionProcessor processor;

    @Scheduled(cron = "${recurring.scheduler.cron:0 0 3 * * *}", zone = "${recurring.scheduler.zone:America/Toronto}")
    public void processDue() {
        processor.processDueRecurringTransactions();
    }
}
