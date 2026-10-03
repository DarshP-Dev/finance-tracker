package com.financetracker.backend.services;

import com.financetracker.backend.dto.RecurringForecastResponse;
import com.financetracker.backend.dto.UpcomingRecurringTransactionResponse;
import com.financetracker.backend.entities.RecurringTransaction;
import com.financetracker.backend.entities.TransactionType;
import com.financetracker.backend.repositories.RecurringTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RecurringForecastService {

    private static final int MAX_OCCURRENCES_PER_DEFINITION = 1_000;
    private static final int MAX_TOTAL_OCCURRENCES = 10_000;
    private static final int MAX_ADVANCES_PER_DEFINITION = 10_000;

    private final AuthenticatedUserService authenticatedUserService;
    private final RecurringTransactionRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<UpcomingRecurringTransactionResponse> getUpcoming(
            Authentication authentication, LocalDate from, LocalDate to
    ) {
        return project(authentication, from, to).occurrences();
    }

    @Transactional(readOnly = true)
    public RecurringForecastResponse getForecast(
            Authentication authentication, LocalDate from, LocalDate to
    ) {
        return getForecastWithUpcoming(authentication, from, to).forecast();
    }

    /** Shares one projection between summary rules and upcoming-occurrence rules. */
    @Transactional(readOnly = true)
    public ForecastWithUpcoming getForecastWithUpcoming(
            Authentication authentication, LocalDate from, LocalDate to
    ) {
        Projection projection = project(authentication, from, to);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        int incomeCount = 0;
        int expenseCount = 0;
        for (UpcomingRecurringTransactionResponse occurrence : projection.occurrences()) {
            if (occurrence.type() == TransactionType.INCOME) {
                income = income.add(occurrence.amount());
                incomeCount++;
            } else {
                expenses = expenses.add(occurrence.amount());
                expenseCount++;
            }
        }
        return new ForecastWithUpcoming(new RecurringForecastResponse(projection.from(), projection.to(), income, expenses,
                income.subtract(expenses), incomeCount, expenseCount), List.copyOf(projection.occurrences()));
    }

    public record ForecastWithUpcoming(RecurringForecastResponse forecast,
                                      List<UpcomingRecurringTransactionResponse> upcoming) {}

    private Projection project(Authentication authentication, LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate from = requestedFrom == null ? LocalDate.now(clock) : requestedFrom;
        LocalDate to = requestedTo == null ? from.plusDays(30) : requestedTo;
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
        }
        if (to.isAfter(from.plusMonths(12))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast range must be 12 months or less");
        }

        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        List<UpcomingRecurringTransactionResponse> occurrences = new ArrayList<>();
        for (RecurringTransaction recurring : repository.findForecastCandidates(userId, from, to)) {
            if (!recurring.isActive()) {
                continue;
            }
            LocalDate scheduledDate = recurring.getNextOccurrence();
            int advances = 0;
            int projected = 0;
            while (!scheduledDate.isAfter(to)
                    && (recurring.getEndDate() == null || !scheduledDate.isAfter(recurring.getEndDate()))) {
                if (++advances > MAX_ADVANCES_PER_DEFINITION) {
                    throw projectionLimitExceeded();
                }
                if (!scheduledDate.isBefore(from)) {
                    if (++projected > MAX_OCCURRENCES_PER_DEFINITION
                            || occurrences.size() >= MAX_TOTAL_OCCURRENCES) {
                        throw projectionLimitExceeded();
                    }
                    occurrences.add(new UpcomingRecurringTransactionResponse(
                            recurring.getId(), recurring.getDescription(), recurring.getMerchant(),
                            recurring.getAmount(), recurring.getCategory(), recurring.getType(),
                            recurring.getFrequency(), scheduledDate));
                }
                LocalDate next = RecurringDateCalculator.next(
                        recurring.getStartDate(), scheduledDate, recurring.getFrequency());
                if (!next.isAfter(scheduledDate)) {
                    throw new IllegalStateException("Recurring date must advance");
                }
                scheduledDate = next;
            }
        }
        occurrences.sort(Comparator.comparing(UpcomingRecurringTransactionResponse::scheduledDate)
                .thenComparing(UpcomingRecurringTransactionResponse::recurringTransactionId));
        return new Projection(from, to, occurrences);
    }

    private ResponseStatusException projectionLimitExceeded() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Forecast has too many occurrences; choose a shorter date range");
    }

    private record Projection(LocalDate from, LocalDate to, List<UpcomingRecurringTransactionResponse> occurrences) {
    }
}
