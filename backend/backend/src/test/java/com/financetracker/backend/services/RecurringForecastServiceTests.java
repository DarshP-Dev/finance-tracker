package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financetracker.backend.dto.RecurringForecastResponse;
import com.financetracker.backend.dto.UpcomingRecurringTransactionResponse;
import com.financetracker.backend.entities.RecurringFrequency;
import com.financetracker.backend.entities.RecurringTransaction;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.RecurringTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RecurringForecastServiceTests {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @Mock private AuthenticatedUserService authenticatedUserService;
    @Mock private RecurringTransactionRepository repository;
    @Mock private Authentication authentication;

    private RecurringForecastService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneId.of("America/Toronto"));
        service = new RecurringForecastService(authenticatedUserService, repository, clock);
    }

    @Test
    void beginsAtNextOccurrenceAndDoesNotShowTheAlreadyGeneratedStartDate() {
        RecurringTransaction netflix = schedule(3L, "Netflix", "50.00", TransactionType.EXPENSE,
                RecurringFrequency.MONTHLY, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 30), null);
        netflix.setLastGeneratedDate(LocalDate.of(2026, 9, 30));
        givenCandidates(List.of(netflix));

        List<UpcomingRecurringTransactionResponse> result = service.getUpcoming(
                authentication, FROM, LocalDate.of(2026, 11, 30));

        assertThat(result).extracting(UpcomingRecurringTransactionResponse::scheduledDate)
                .containsExactly(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 30));
        assertThat(netflix.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(netflix.getLastGeneratedDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({
            "WEEKLY,2026-10-01,2026-10-08,2026-10-15",
            "BIWEEKLY,2026-10-01,2026-10-15,2026-10-29",
            "MONTHLY,2026-01-31,2026-10-31,2026-11-30",
            "YEARLY,2024-02-29,2026-02-28,2027-02-28"
    })
    void projectionsReuseTheCalendarAnchoredFrequencyCalculator(
            RecurringFrequency frequency, LocalDate start, LocalDate next, LocalDate following
    ) {
        givenCandidates(List.of(schedule(1L, "Recurring", "10.00", TransactionType.EXPENSE,
                frequency, start, next, null)));

        List<UpcomingRecurringTransactionResponse> result = service.getUpcoming(
                authentication, next, following);

        assertThat(result).extracting(UpcomingRecurringTransactionResponse::scheduledDate)
                .containsExactly(next, following);
    }

    @Test
    void pausedDefinitionsAreExcludedEvenIfAStaleQueryReturnsOne() {
        RecurringTransaction paused = schedule(1L, "Paused", "10.00", TransactionType.EXPENSE,
                RecurringFrequency.MONTHLY, FROM, FROM, null);
        paused.setActive(false);
        givenCandidates(List.of(paused));

        assertThat(service.getUpcoming(authentication, FROM, TO)).isEmpty();
    }

    @Test
    void endDateIsInclusiveWithoutDeactivatingTheDefinition() {
        LocalDate end = LocalDate.of(2026, 11, 30);
        RecurringTransaction netflix = schedule(1L, "Netflix", "50.00", TransactionType.EXPENSE,
                RecurringFrequency.MONTHLY, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 30), end);
        givenCandidates(List.of(netflix));

        assertThat(service.getUpcoming(authentication, FROM, TO))
                .extracting(UpcomingRecurringTransactionResponse::scheduledDate)
                .containsExactly(LocalDate.of(2026, 10, 30), end);
        assertThat(netflix.isActive()).isTrue();
        assertThat(netflix.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void forecastTotalsUseBigDecimalAndCombineIncomeWithExpenses() {
        RecurringTransaction income = schedule(2L, "Paycheque", "1200.25", TransactionType.INCOME,
                RecurringFrequency.MONTHLY, LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5), null);
        RecurringTransaction expense = schedule(3L, "Netflix", "50.10", TransactionType.EXPENSE,
                RecurringFrequency.MONTHLY, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 30), null);
        givenCandidates(List.of(expense, income));

        RecurringForecastResponse result = service.getForecast(
                authentication, FROM, LocalDate.of(2026, 11, 30));

        assertThat(result.expectedIncome()).isEqualByComparingTo("2400.50");
        assertThat(result.expectedExpenses()).isEqualByComparingTo("100.20");
        assertThat(result.netCashFlow()).isEqualByComparingTo("2300.30");
        assertThat(result.incomeOccurrenceCount()).isEqualTo(2);
        assertThat(result.expenseOccurrenceCount()).isEqualTo(2);
    }

    @Test
    void occurrencesAreSortedByDateThenStableDefinitionId() {
        RecurringTransaction laterId = schedule(9L, "Later ID", "10.00", TransactionType.EXPENSE,
                RecurringFrequency.MONTHLY, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 10, 30));
        RecurringTransaction earlierId = schedule(2L, "Earlier ID", "10.00", TransactionType.EXPENSE,
                RecurringFrequency.MONTHLY, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 10, 30));
        RecurringTransaction earlierDate = schedule(5L, "Earlier date", "10.00", TransactionType.EXPENSE,
                RecurringFrequency.MONTHLY, LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 5));
        givenCandidates(List.of(laterId, earlierId, earlierDate));

        assertThat(service.getUpcoming(authentication, FROM, TO))
                .extracting(UpcomingRecurringTransactionResponse::recurringTransactionId)
                .containsExactly(5L, 2L, 9L);
    }

    @Test
    void defaultRangeStartsTodayAndEndsThirtyDaysLater() {
        givenCandidates(List.of());

        RecurringForecastResponse result = service.getForecast(authentication, null, null);

        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(result.to()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(result.netCashFlow()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsReversedAndOversizedDateRanges() {
        assertBadRange(FROM, FROM.minusDays(1));
        assertBadRange(FROM, FROM.plusMonths(12).plusDays(1));
    }

    @Test
    void anOldScheduleBeyondProjectionSafetyLimitReturnsAnError() {
        givenCandidates(List.of(schedule(1L, "Ancient", "1.00", TransactionType.EXPENSE,
                RecurringFrequency.WEEKLY, LocalDate.of(1800, 1, 1), LocalDate.of(1800, 1, 8), null)));

        assertBadRange(FROM, FROM.plusDays(1));
    }

    private void assertBadRange(LocalDate from, LocalDate to) {
        assertThatThrownBy(() -> service.getUpcoming(authentication, from, to))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void givenCandidates(List<RecurringTransaction> candidates) {
        when(authenticatedUserService.getCurrentUser(authentication))
                .thenReturn(User.builder().id(25L).email("owner@example.com").build());
        when(repository.findForecastCandidates(eq(25L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(candidates);
    }

    private RecurringTransaction schedule(
            Long id, String description, String amount, TransactionType type, RecurringFrequency frequency,
            LocalDate start, LocalDate next, LocalDate end
    ) {
        return RecurringTransaction.builder()
                .id(id).user(User.builder().id(25L).build())
                .amount(new BigDecimal(amount)).category(TransactionCategory.ENTERTAINMENT)
                .type(type).description(description).merchant(description)
                .frequency(frequency).startDate(start).nextOccurrence(next).endDate(end).build();
    }
}
