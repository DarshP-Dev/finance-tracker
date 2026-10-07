package com.financetracker.backend.services;

import com.financetracker.backend.repositories.RecurringTransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class RecurringTransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionProcessor.class);
    private static final int BATCH_SIZE = 250;

    private final RecurringTransactionRepository repository;
    private final RecurringTransactionService recurringTransactionService;
    private final Clock clock;
    private final int maxCatchUp;
    private final ApplicationEventPublisher events;

    public RecurringTransactionProcessor(
            RecurringTransactionRepository repository,
            RecurringTransactionService recurringTransactionService,
            Clock clock,
            ApplicationEventPublisher events,
            @Value("${recurring.scheduler.max-catch-up:100}") int maxCatchUp
    ) {
        if (maxCatchUp < 1) {
            throw new IllegalArgumentException("recurring.scheduler.max-catch-up must be positive");
        }
        this.repository = repository;
        this.recurringTransactionService = recurringTransactionService;
        this.clock = clock;
        this.maxCatchUp = maxCatchUp;
        this.events = events;
    }

    public ProcessingResult processDueRecurringTransactions() {
        LocalDate today = LocalDate.now(clock);
        long afterId = 0L;
        int dueDefinitions = 0;
        int generatedOccurrences = 0;
        int failedDefinitions = 0;

        while (true) {
            List<Long> ids = repository.findDueIds(today, afterId, PageRequest.of(0, BATCH_SIZE));
            if (ids.isEmpty()) {
                break;
            }
            if (dueDefinitions == 0) {
                log.info("Recurring transaction processing started for {}", today);
            }
            for (Long id : ids) {
                dueDefinitions++;
                try {
                    RecurringTransactionService.DueProcessingResult result =
                            recurringTransactionService.processDueRecurringTransaction(id, today, maxCatchUp);
                    int generated = result.generated();
                    generatedOccurrences += generated;
                    if (generated > 0) {
                        log.info("Processed recurring transaction ID {}: {} occurrence(s)", id, generated);
                    }
                    if (result.expired()) {
                        log.info("Recurring transaction ID {} expired", id);
                    }
                    if (result.limitReached()) {
                        log.warn("Catch-up limit reached for recurring transaction ID {}", id);
                    }
                } catch (RuntimeException exception) {
                    failedDefinitions++;
                    log.error("Recurring transaction processing failed for ID {} ({})", id,
                            exception.getClass().getSimpleName());
                    events.publishEvent(new FinancialNotificationEvents.RecurringFailed(id));
                }
            }
            afterId = ids.getLast();
            if (ids.size() < BATCH_SIZE) {
                break;
            }
        }

        if (dueDefinitions > 0) {
            log.info("Recurring transaction processing complete: {} due, {} generated, {} failed",
                    dueDefinitions, generatedOccurrences, failedDefinitions);
        }
        events.publishEvent(new FinancialNotificationEvents.CheckUpcoming());
        return new ProcessingResult(dueDefinitions, generatedOccurrences, failedDefinitions);
    }

    public record ProcessingResult(int dueDefinitions, int generatedOccurrences, int failedDefinitions) {
    }
}
