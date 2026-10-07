package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financetracker.backend.repositories.RecurringTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionProcessorTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @Mock private RecurringTransactionRepository repository;
    @Mock private RecurringTransactionService service;
    @Mock private org.springframework.context.ApplicationEventPublisher events;

    private RecurringTransactionProcessor processor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneId.of("America/Toronto"));
        processor = new RecurringTransactionProcessor(repository, service, clock,
                events, 100);
    }

    @Test
    void processesOnlyDueIdsReturnedByTheRepository() {
        when(repository.findDueIds(eq(TODAY), eq(0L), any(Pageable.class))).thenReturn(List.of(3L));
        when(service.processDueRecurringTransaction(3L, TODAY, 100))
                .thenReturn(new RecurringTransactionService.DueProcessingResult(1, false, false));

        assertThat(processor.processDueRecurringTransactions())
                .isEqualTo(new RecurringTransactionProcessor.ProcessingResult(1, 1, 0));
        verify(service).processDueRecurringTransaction(3L, TODAY, 100);
    }

    @Test
    void emptyDueQueryDoesNotAttemptGeneration() {
        when(repository.findDueIds(eq(TODAY), eq(0L), any(Pageable.class))).thenReturn(List.of());

        assertThat(processor.processDueRecurringTransactions())
                .isEqualTo(new RecurringTransactionProcessor.ProcessingResult(0, 0, 0));
        verify(service, never()).processDueRecurringTransaction(any(), any(), anyInt());
        verify(events).publishEvent(new FinancialNotificationEvents.CheckUpcoming());
    }

    @Test
    void oneFailedDefinitionDoesNotStopFollowingDefinitions() {
        when(repository.findDueIds(eq(TODAY), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(service.processDueRecurringTransaction(1L, TODAY, 100))
                .thenReturn(new RecurringTransactionService.DueProcessingResult(1, false, false));
        when(service.processDueRecurringTransaction(2L, TODAY, 100))
                .thenThrow(new IllegalStateException("simulated failure"));
        when(service.processDueRecurringTransaction(3L, TODAY, 100))
                .thenReturn(new RecurringTransactionService.DueProcessingResult(2, false, false));

        assertThat(processor.processDueRecurringTransactions())
                .isEqualTo(new RecurringTransactionProcessor.ProcessingResult(3, 3, 1));
        verify(service).processDueRecurringTransaction(3L, TODAY, 100);
        verify(events).publishEvent(new FinancialNotificationEvents.RecurringFailed(2L));
    }
}
