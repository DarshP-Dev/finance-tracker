package com.financetracker.backend.services.market;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** Regular US equity session only; no network request is needed to check the calendar.
 * Scheduled NYSE holidays/early closes; extraordinary closures are not predicted. */
final class UsEquityMarketSession {
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    record Session(LocalDate date, Instant opensAt, Instant closesAt) {}

    static LocalDate easternDate(Instant now) { return now.atZone(EASTERN).toLocalDate(); }

    static Session latestCompletedSession(Instant now) {
        LocalDate date = easternDate(now);
        while (!tradingDay(date) || now.isBefore(date.atTime(close(date)).atZone(EASTERN).toInstant()))
            date = date.minusDays(1);
        return new Session(date, date.atTime(OPEN).atZone(EASTERN).toInstant(),
                date.atTime(close(date)).atZone(EASTERN).toInstant());
    }

    static boolean isOpen(Instant now) {
        var local = now.atZone(EASTERN);
        return tradingDay(local.toLocalDate()) && !local.toLocalTime().isBefore(OPEN)
                && local.toLocalTime().isBefore(close(local.toLocalDate()));
    }

    /** Opening of the most recent session, including the current day after its open. */
    static Instant latestSessionOpen(Instant now) {
        var local = now.atZone(EASTERN);
        LocalDate date = local.toLocalDate();
        if (local.toLocalTime().isBefore(OPEN)) date = date.minusDays(1);
        while (!tradingDay(date)) date = date.minusDays(1);
        return date.atTime(OPEN).atZone(EASTERN).toInstant();
    }

    private static boolean tradingDay(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) return false;
        int year = date.getYear();
        // NYSE does not observe Saturday New Year's Day on the preceding Friday.
        LocalDate newYear = LocalDate.of(year, 1, 1);
        if (newYear.getDayOfWeek() == DayOfWeek.SUNDAY) newYear = newYear.plusDays(1);
        return !date.equals(newYear)
                && !date.equals(nth(year, 1, DayOfWeek.MONDAY, 3))
                && !date.equals(nth(year, 2, DayOfWeek.MONDAY, 3))
                && !date.equals(easter(year).minusDays(2))
                && !date.equals(LocalDate.of(year, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)))
                && !(year >= 2022 && date.equals(observed(LocalDate.of(year, 6, 19))))
                && !date.equals(observed(LocalDate.of(year, 7, 4)))
                && !date.equals(nth(year, 9, DayOfWeek.MONDAY, 1))
                && !date.equals(nth(year, 11, DayOfWeek.THURSDAY, 4))
                && !date.equals(observed(LocalDate.of(year, 12, 25)));
    }

    private static LocalTime close(LocalDate date) {
        boolean early = date.equals(nth(date.getYear(), 11, DayOfWeek.THURSDAY, 4).plusDays(1))
                || (date.getMonthValue() == 7 && date.getDayOfMonth() == 3)
                || (date.getMonthValue() == 12 && date.getDayOfMonth() == 24);
        return LocalTime.of(early ? 13 : 16, 0);
    }

    private static LocalDate nth(int year, int month, DayOfWeek weekday, int n) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, weekday));
    }

    private static LocalDate observed(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> date.minusDays(1);
            case SUNDAY -> date.plusDays(1);
            default -> date;
        };
    }

    private static LocalDate easter(int year) {
        // Gregorian computus, used only for the scheduled Good Friday closure.
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int value = h + l - 7 * m + 114;
        return LocalDate.of(year, value / 31, value % 31 + 1);
    }
}
