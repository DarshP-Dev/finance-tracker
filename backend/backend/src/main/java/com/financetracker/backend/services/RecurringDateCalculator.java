package com.financetracker.backend.services;

import com.financetracker.backend.entities.RecurringFrequency;
import java.time.LocalDate;
import java.time.YearMonth;

/** Keeps monthly and yearly occurrences anchored to the original start date. */
public final class RecurringDateCalculator {

    private RecurringDateCalculator() {
    }

    public static LocalDate next(LocalDate startDate, LocalDate current, RecurringFrequency frequency) {
        return switch (frequency) {
            case WEEKLY -> current.plusWeeks(1);
            case BIWEEKLY -> current.plusWeeks(2);
            case MONTHLY -> {
                YearMonth nextMonth = YearMonth.from(current).plusMonths(1);
                yield nextMonth.atDay(Math.min(startDate.getDayOfMonth(), nextMonth.lengthOfMonth()));
            }
            case YEARLY -> startDate.plusYears(current.getYear() + 1L - startDate.getYear());
        };
    }

    public static LocalDate firstAfter(LocalDate startDate, LocalDate lastGeneratedDate, RecurringFrequency frequency) {
        LocalDate candidate = startDate;
        while (lastGeneratedDate != null && !candidate.isAfter(lastGeneratedDate)) {
            candidate = next(startDate, candidate, frequency);
        }
        return candidate;
    }
}
