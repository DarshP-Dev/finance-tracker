package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.backend.entities.RecurringFrequency;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurringDateCalculatorTests {

    @Test
    void advancesWeeklyAndBiweeklyDates() {
        LocalDate start = LocalDate.of(2026, 10, 12);

        assertThat(RecurringDateCalculator.next(start, start, RecurringFrequency.WEEKLY))
                .isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(RecurringDateCalculator.next(start, start, RecurringFrequency.BIWEEKLY))
                .isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void preservesTheOriginalDayAcrossShortMonths() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        LocalDate february = RecurringDateCalculator.next(start, start, RecurringFrequency.MONTHLY);

        assertThat(february).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(RecurringDateCalculator.next(start, february, RecurringFrequency.MONTHLY))
                .isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void preservesLeapDayInLaterLeapYears() {
        LocalDate start = LocalDate.of(2024, 2, 29);
        LocalDate year2025 = RecurringDateCalculator.next(start, start, RecurringFrequency.YEARLY);

        assertThat(year2025).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(RecurringDateCalculator.next(start, year2025, RecurringFrequency.YEARLY))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(RecurringDateCalculator.firstAfter(start, LocalDate.of(2027, 2, 28), RecurringFrequency.YEARLY))
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void editedScheduleStartsAfterTheLastGeneratedDate() {
        assertThat(RecurringDateCalculator.firstAfter(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), RecurringFrequency.MONTHLY))
                .isEqualTo(LocalDate.of(2026, 3, 31));
    }
}
