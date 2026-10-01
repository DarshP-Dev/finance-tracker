package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financetracker.backend.dto.TransactionRequest;
import com.financetracker.backend.dto.TransactionResponse;
import com.financetracker.backend.entities.RecurringFrequency;
import com.financetracker.backend.entities.RecurringTransaction;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.RecurringTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionPhase3Tests {

    @Mock private AuthenticatedUserService authenticatedUserService;
    @Mock private RecurringTransactionRepository repository;
    @Mock private TransactionService transactionService;

    private RecurringTransactionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RecurringTransactionService(authenticatedUserService, repository, transactionService);
        user = User.builder().id(25L).email("owner@example.com").build();
    }

    @Test
    void netflixStartsFromNextOccurrenceAndNeverRegeneratesTheInitialCharge() {
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 30), RecurringFrequency.MONTHLY, null);
        recurring.setLastGeneratedDate(LocalDate.of(2026, 9, 30));
        givenLocked(recurring);

        assertThat(service.processDueRecurringTransaction(10L, LocalDate.of(2026, 10, 30), 100).generated())
                .isEqualTo(1);
        assertThat(generatedDates()).containsExactly(LocalDate.of(2026, 10, 30));
        assertThat(recurring.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(recurring.getLastGeneratedDate()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void futureOccurrenceIsIgnoredEvenIfItWasSelectedBeforeTheDateChanged() {
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 30), RecurringFrequency.MONTHLY, null);
        givenLocked(recurring);

        assertThat(service.processDueRecurringTransaction(10L, LocalDate.of(2026, 10, 1), 100).generated())
                .isZero();
        verify(transactionService, never()).createTransactionForUser(any(), any());
        assertThat(recurring.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void pausedOccurrenceIsIgnoredWithoutMovingTheSchedule() {
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 30), RecurringFrequency.MONTHLY, null);
        recurring.setActive(false);
        givenLocked(recurring);

        assertThat(service.processDueRecurringTransaction(10L, LocalDate.of(2026, 10, 30), 100).generated())
                .isZero();
        verify(transactionService, never()).createTransactionForUser(any(), any());
        assertThat(recurring.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @ParameterizedTest
    @CsvSource({
            "WEEKLY,2026-09-30,2026-10-07,2026-10-14",
            "BIWEEKLY,2026-09-30,2026-10-14,2026-10-28",
            "MONTHLY,2026-01-31,2026-02-28,2026-03-31",
            "YEARLY,2024-02-29,2025-02-28,2026-02-28"
    })
    void advancesEachFrequencyUsingItsCalendarAnchor(
            RecurringFrequency frequency, LocalDate start, LocalDate next, LocalDate following
    ) {
        RecurringTransaction recurring = schedule(start, next, frequency, null);
        givenLocked(recurring);

        assertThat(service.processDueRecurringTransaction(10L, next, 100).generated()).isEqualTo(1);
        assertThat(generatedDates()).containsExactly(next);
        assertThat(recurring.getNextOccurrence()).isEqualTo(following);
    }

    @Test
    void catchesUpEveryMissedDateBeginningAtNextOccurrence() {
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 7, 30), RecurringFrequency.MONTHLY, null);
        recurring.setLastGeneratedDate(LocalDate.of(2026, 6, 30));
        givenLocked(recurring);

        assertThat(service.processDueRecurringTransaction(10L, LocalDate.of(2026, 10, 30), 100).generated())
                .isEqualTo(4);
        assertThat(generatedDates()).containsExactly(
                LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 30));
        assertThat(recurring.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void endDateIsInclusiveAndDeactivatesAfterTheFinalCharge() {
        LocalDate finalDate = LocalDate.of(2026, 10, 30);
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 9, 30), finalDate,
                RecurringFrequency.MONTHLY, finalDate);
        givenLocked(recurring);

        RecurringTransactionService.DueProcessingResult result =
                service.processDueRecurringTransaction(10L, finalDate, 100);

        assertThat(result.generated()).isEqualTo(1);
        assertThat(result.expired()).isTrue();
        assertThat(generatedDates()).containsExactly(finalDate);
        assertThat(recurring.isActive()).isFalse();
        assertThat(recurring.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void anAlreadyExpiredDefinitionDoesNotGenerate() {
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 30), RecurringFrequency.MONTHLY, LocalDate.of(2026, 10, 1));
        givenLocked(recurring);

        RecurringTransactionService.DueProcessingResult result =
                service.processDueRecurringTransaction(10L, LocalDate.of(2026, 10, 30), 100);

        assertThat(result.generated()).isZero();
        assertThat(result.expired()).isTrue();
        assertThat(recurring.isActive()).isFalse();
        verify(transactionService, never()).createTransactionForUser(any(), any());
    }

    @Test
    void catchUpLimitLeavesTheNextMissedDateReadyForAnotherRun() {
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 7, 30), RecurringFrequency.MONTHLY, null);
        givenLocked(recurring);

        RecurringTransactionService.DueProcessingResult result =
                service.processDueRecurringTransaction(10L, LocalDate.of(2026, 10, 30), 2);

        assertThat(result.generated()).isEqualTo(2);
        assertThat(result.limitReached()).isTrue();
        assertThat(generatedDates()).containsExactly(LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 30));
        assertThat(recurring.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(recurring.isActive()).isTrue();
    }

    @Test
    void refusesToRegenerateAnAlreadyRecordedOccurrence() {
        LocalDate date = LocalDate.of(2026, 10, 30);
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 9, 30), date,
                RecurringFrequency.MONTHLY, null);
        recurring.setLastGeneratedDate(date);
        givenLocked(recurring);

        assertThatThrownBy(() -> service.processDueRecurringTransaction(10L, date, 100))
                .isInstanceOf(IllegalStateException.class);
        verify(transactionService, never()).createTransactionForUser(any(), any());
        assertThat(recurring.getNextOccurrence()).isEqualTo(date);
    }

    @Test
    void transactionCreationFailureDoesNotAdvanceTheOccurrence() {
        LocalDate date = LocalDate.of(2026, 10, 30);
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 9, 30), date,
                RecurringFrequency.MONTHLY, null);
        givenLocked(recurring);
        when(transactionService.createTransactionForUser(any(), any()))
                .thenThrow(new IllegalStateException("simulated failure"));

        assertThatThrownBy(() -> service.processDueRecurringTransaction(10L, date, 100))
                .isInstanceOf(IllegalStateException.class);
        assertThat(recurring.getNextOccurrence()).isEqualTo(date);
        assertThat(recurring.getLastGeneratedDate()).isNull();
    }

    @Test
    void manualAndAutomaticGenerationAdvancePastTheSameDate() {
        LocalDate date = LocalDate.of(2026, 10, 30);
        RecurringTransaction recurring = schedule(LocalDate.of(2026, 9, 30), date,
                RecurringFrequency.MONTHLY, null);
        givenLocked(recurring);
        when(repository.findByIdAndUserIdForUpdate(10L, 25L)).thenReturn(Optional.of(recurring));
        var authentication = new UsernamePasswordAuthenticationToken("owner@example.com", null);
        when(authenticatedUserService.getCurrentUser(authentication)).thenReturn(user);
        when(transactionService.createTransactionForUser(any(), any()))
                .thenAnswer(invocation -> TransactionResponse.builder()
                        .date(((TransactionRequest) invocation.getArgument(1)).getDate()).build());

        service.processDueRecurringTransaction(10L, date, 100);
        service.generateNextTransaction(authentication, 10L);

        assertThat(generatedDates()).containsExactly(date, LocalDate.of(2026, 11, 30));
        assertThat(recurring.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 12, 30));
    }

    private void givenLocked(RecurringTransaction recurring) {
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(recurring));
    }

    private java.util.List<LocalDate> generatedDates() {
        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService, atLeastOnce()).createTransactionForUser(any(), captor.capture());
        return captor.getAllValues().stream().map(TransactionRequest::getDate).toList();
    }

    private RecurringTransaction schedule(
            LocalDate start, LocalDate next, RecurringFrequency frequency, LocalDate end
    ) {
        return RecurringTransaction.builder()
                .id(10L).user(user).amount(new BigDecimal("50.00"))
                .category(TransactionCategory.ENTERTAINMENT).type(TransactionType.EXPENSE)
                .description("Netflix").merchant("Netflix").frequency(frequency)
                .startDate(start).nextOccurrence(next).endDate(end).build();
    }
}
