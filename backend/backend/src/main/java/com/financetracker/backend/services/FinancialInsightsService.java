package com.financetracker.backend.services;

import static com.financetracker.backend.dto.FinancialInsightResponse.Severity.*;
import static com.financetracker.backend.dto.FinancialInsightResponse.Type.*;

import com.financetracker.backend.dto.AnalyticsResponse;
import com.financetracker.backend.dto.FinancialInsightResponse;
import com.financetracker.backend.dto.FinancialInsightResponse.Severity;
import com.financetracker.backend.dto.FinancialInsightResponse.Type;
import com.financetracker.backend.dto.FinancialInsightsResponse;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only rules over existing analytics and recurring projections. No stored insights. */
@Service
@RequiredArgsConstructor
public class FinancialInsightsService {
    public static final int MAX_INSIGHTS = 8;
    private static final BigDecimal CHANGE_THRESHOLD = new BigDecimal("10");
    private static final BigDecimal CATEGORY_BASE_MIN = new BigDecimal("50");
    private static final BigDecimal CATEGORY_CHANGE_MIN = new BigDecimal("25");
    private static final BigDecimal SAVINGS_CHANGE_MIN = new BigDecimal("5");
    private final AnalyticsService analytics;
    private final RecurringForecastService recurring;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FinancialInsightsResponse generateInsightsForUser(Authentication authentication) {
        LocalDate today = LocalDate.now(clock);
        Instant generatedAt = clock.instant();
        var current = analytics.getAnalytics(authentication, AnalyticsService.Period.THIS_MONTH, today);
        var previous = analytics.getAnalytics(authentication, AnalyticsService.Period.LAST_MONTH, today);
        var context = new Context(today, generatedAt, current.startDate(), current.endDate());
        List<Candidate> candidates = new ArrayList<>();
        Set<TransactionCategory> budgetWarnings = budgetInsights(current, context, candidates);
        spendingInsights(current, previous, context, budgetWarnings, candidates);
        savingsAndCashFlow(current, previous, context, candidates);
        recurringInsights(authentication, context, candidates);
        if (current.investments().uniqueHoldings() > 0) {
            add(candidates, context, 90, "recorded-investments", INVESTMENT, INFO, "Recorded investments",
                    "You track " + current.investments().uniqueHoldings() + " investment "
                            + (current.investments().uniqueHoldings() == 1 ? "position" : "positions") + " with "
                            + money(current.investments().totalInvestedAllTime())
                            + " in recorded purchases. This is purchase cost, not current market value.",
                    current.investments().totalInvestedAllTime(), null, null, null, null);
        }
        Set<String> keys = new HashSet<>();
        var insights = candidates.stream()
                .sorted(Comparator.comparingInt(Candidate::priority)
                        .thenComparing(Candidate::importance, Comparator.reverseOrder())
                        .thenComparing(candidate -> candidate.insight().key()))
                .filter(candidate -> keys.add(candidate.insight().key()))
                .limit(MAX_INSIGHTS).map(Candidate::insight).toList();
        return new FinancialInsightsResponse(generatedAt, insights);
    }

    private Set<TransactionCategory> budgetInsights(AnalyticsResponse data, Context context, List<Candidate> out) {
        Set<TransactionCategory> warnings = new HashSet<>();
        BigDecimal elapsed = percent(BigDecimal.valueOf(context.today().getDayOfMonth()),
                BigDecimal.valueOf(context.today().lengthOfMonth()));
        data.budgets().categories().stream()
                .sorted(Comparator.comparing(AnalyticsResponse.BudgetCategory::percentUsed).reversed()
                        .thenComparing(b -> b.category().name()))
                .filter(b -> b.percentUsed().compareTo(new BigDecimal("80")) >= 0
                        || (b.percentUsed().compareTo(new BigDecimal("40")) >= 0
                            && b.percentUsed().subtract(elapsed).compareTo(new BigDecimal("20")) >= 0))
                .limit(3).forEach(b -> {
                    warnings.add(b.category());
                    String label = label(b.category());
                    String message;
                    int priority;
                    if (b.remaining().signum() < 0) {
                        message = "You've exceeded your " + label + " budget by " + money(b.remaining().abs()) + ".";
                        priority = 10;
                    } else if (b.percentUsed().compareTo(new BigDecimal("100")) >= 0) {
                        message = "You've used 100% of your " + label + " budget.";
                        priority = 10;
                    } else {
                        message = "You've used " + number(b.percentUsed()) + "% of your " + label + " budget.";
                        if (b.percentUsed().subtract(elapsed).compareTo(new BigDecimal("20")) >= 0) {
                            message += " The month is " + number(elapsed)
                                    + "% complete; recorded budget usage is ahead of time-based pace.";
                        }
                        priority = 20;
                    }
                    add(out, context, priority, "budget-" + b.category().name().toLowerCase(Locale.ROOT),
                            BUDGET, WARNING, label + " budget", message, b.percentUsed(), b.remaining(), b.category(),
                            data.budgets().month().atDay(1), data.budgets().month().atEndOfMonth());
                });
        return warnings;
    }

    private void spendingInsights(AnalyticsResponse current, AnalyticsResponse previous, Context context,
            Set<TransactionCategory> budgetWarnings, List<Candidate> out) {
        BigDecimal expenses = current.overview().totalExpenses();
        BigDecimal oldExpenses = previous.overview().totalExpenses();
        if (oldExpenses.signum() > 0) {
            BigDecimal change = percent(expenses.subtract(oldExpenses), oldExpenses);
            if (change.abs().compareTo(CHANGE_THRESHOLD) >= 0) {
                add(out, context, change.signum() > 0 ? 45 : 75, "spending-change", SPENDING,
                        change.signum() > 0 ? WARNING : POSITIVE, "Spending change",
                        "Your spending this month to date is " + number(change.abs()) + "% "
                                + (change.signum() > 0 ? "higher" : "lower")
                                + " than the full previous calendar month.", change, oldExpenses, null);
            }
        }
        var oldCategories = new EnumMap<TransactionCategory, BigDecimal>(TransactionCategory.class);
        previous.spendingByCategory().forEach(c -> oldCategories.put(c.category(), c.amount()));
        var newCategories = new EnumMap<TransactionCategory, BigDecimal>(TransactionCategory.class);
        current.spendingByCategory().forEach(c -> newCategories.put(c.category(), c.amount()));
        oldCategories.entrySet().stream()
                .filter(e -> !budgetWarnings.contains(e.getKey()) && e.getValue().compareTo(CATEGORY_BASE_MIN) >= 0)
                .map(e -> new CategoryChange(e.getKey(), e.getValue(),
                        newCategories.getOrDefault(e.getKey(), BigDecimal.ZERO).subtract(e.getValue())))
                .filter(c -> c.delta().abs().compareTo(CATEGORY_CHANGE_MIN) >= 0
                        && percent(c.delta(), c.previous()).abs().compareTo(CHANGE_THRESHOLD) >= 0)
                .sorted(Comparator.comparing((CategoryChange c) -> c.delta().abs()).reversed()
                        .thenComparing(c -> c.category().name()))
                .limit(3).forEach(c -> {
                    BigDecimal change = percent(c.delta(), c.previous());
                    add(out, context, change.signum() > 0 ? 40 : 75,
                            "category-" + c.category().name().toLowerCase(Locale.ROOT), SPENDING,
                            change.signum() > 0 ? WARNING : POSITIVE, label(c.category()) + " spending",
                            label(c.category()) + " spending this month to date is " + number(change.abs()) + "% "
                                    + (change.signum() > 0 ? "higher" : "lower")
                                    + " than the full previous calendar month.", change, c.previous(), c.category());
                });
        // When a single category accounts for both period totals, its change already
        // explains the overall change. Keep the more specific insight.
        boolean totalAlreadyExplained = out.stream().map(Candidate::insight)
                .filter(i -> i.type() == SPENDING && i.category() != null)
                .anyMatch(i -> oldCategories.getOrDefault(i.category(), BigDecimal.ZERO).compareTo(oldExpenses) == 0
                        && newCategories.getOrDefault(i.category(), BigDecimal.ZERO).compareTo(expenses) == 0);
        if (totalAlreadyExplained) out.removeIf(c -> c.insight().key().equals("spending-change"));
    }

    private void savingsAndCashFlow(AnalyticsResponse current, AnalyticsResponse previous, Context context,
            List<Candidate> out) {
        var now = current.overview();
        var old = previous.overview();
        if (now.netCashFlow().signum() < 0) {
            BigDecimal rate = now.totalIncome().signum() > 0 ? percent(now.netCashFlow(), now.totalIncome()) : null;
            add(out, context, 30, "income-expenses", INCOME, WARNING, "Income versus expenses",
                    "Your expenses exceed income by " + money(now.netCashFlow().abs()) + " this month to date."
                            + (rate == null ? "" : " Your savings rate is " + number(rate) + "%."),
                    now.netCashFlow(), rate, null);
        }
        if (now.totalIncome().signum() > 0) {
            BigDecimal rate = percent(now.netCashFlow(), now.totalIncome());
            BigDecimal oldRate = old.totalIncome().signum() > 0 ? percent(old.netCashFlow(), old.totalIncome()) : null;
            boolean changed = oldRate != null && rate.subtract(oldRate).abs().compareTo(SAVINGS_CHANGE_MIN) >= 0;
            // A falling/negative savings message otherwise repeats the income-versus-expenses warning.
            if (now.netCashFlow().signum() >= 0 || (changed && rate.compareTo(oldRate) > 0)) {
                String message = changed
                        ? "Your savings rate " + (rate.compareTo(oldRate) > 0 ? "increased" : "decreased")
                            + " from " + number(oldRate) + "% for the full previous calendar month to "
                            + number(rate) + "% this month to date."
                        : "Your savings rate this month to date is " + number(rate) + "%.";
                if (now.netCashFlow().signum() > 0) {
                    message += " Income exceeds expenses by " + money(now.netCashFlow()) + " this month to date.";
                }
                Severity severity = changed ? (rate.compareTo(oldRate) > 0 ? POSITIVE : WARNING)
                        : rate.signum() > 0 ? POSITIVE : INFO;
                add(out, context, changed ? 50 : 80, "savings-rate", SAVINGS, severity, "Savings rate",
                        message, rate, oldRate, null);
            }
        }
    }

    private void recurringInsights(Authentication authentication, Context context, List<Candidate> out) {
        // Exactly 30 inclusive calendar dates: today through today + 29.
        LocalDate end = context.today().plusDays(29);
        var projection = recurring.getForecastWithUpcoming(authentication, context.today(), end);
        var forecast = projection.forecast();
        if (forecast.incomeOccurrenceCount() + forecast.expenseOccurrenceCount() == 0) return;
        String message = "Based on known recurring transactions, you have "
                + money(forecast.expectedIncome()) + " in income and " + money(forecast.expectedExpenses())
                + " in expenses scheduled over the next 30 days.";
        boolean hasNet = forecast.netCashFlow().signum() != 0;
        Severity severity = INFO;
        if (hasNet) {
            boolean positive = forecast.netCashFlow().signum() > 0;
            severity = positive ? POSITIVE : WARNING;
            message += " Known recurring " + (positive ? "income exceeds expenses" : "expenses exceed income")
                            + " by " + money(forecast.netCashFlow().abs())
                            + ".";
        }
        message += " This excludes other future income and spending.";
        // Totals and net share one insight instead of restating the same forecast twice.
        add(out, context, 60, "recurring-summary", hasNet ? FORECAST : RECURRING, severity,
                "Known recurring forecast", message, forecast.netCashFlow(), forecast.expectedExpenses(),
                null, context.today(), end);
        projection.upcoming().stream().filter(o -> o.type() == TransactionType.EXPENSE
                        && !o.scheduledDate().isAfter(context.today().plusDays(6)))
                .sorted(Comparator.comparing(com.financetracker.backend.dto.UpcomingRecurringTransactionResponse::amount)
                        .reversed().thenComparing(o -> o.scheduledDate()).thenComparing(o -> o.recurringTransactionId()))
                .findFirst().ifPresent(o -> {
                    long days = ChronoUnit.DAYS.between(context.today(), o.scheduledDate());
                    add(out, context, 55, "upcoming-largest-expense", RECURRING, INFO,
                            "Largest recurring expense in the next 7 days",
                            (o.description() == null || o.description().isBlank() ? label(o.category()) : o.description())
                                    + " of " + money(o.amount()) + " is scheduled "
                                    + (days == 0 ? "today" : "in " + days + (days == 1 ? " day" : " days")) + ".",
                            o.amount(), null, o.category(), o.scheduledDate(), o.scheduledDate());
                });
    }

    private void add(List<Candidate> out, Context c, int priority, String key, Type type, Severity severity,
            String title, String message, BigDecimal metric, BigDecimal comparison, TransactionCategory category) {
        add(out, c, priority, key, type, severity, title, message, metric, comparison, category, c.from(), c.to());
    }

    private void add(List<Candidate> out, Context c, int priority, String key, Type type, Severity severity,
            String title, String message, BigDecimal metric, BigDecimal comparison, TransactionCategory category,
            LocalDate from, LocalDate to) {
        out.add(new Candidate(priority, metric == null ? BigDecimal.ZERO : metric.abs(),
                new FinancialInsightResponse(key, type, severity, title, message, metric, comparison, category,
                        from, to, c.generatedAt())));
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }
    private static String number(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    private static String money(BigDecimal value) {
        return "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static String label(TransactionCategory category) {
        String text = category.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    private record Context(LocalDate today, Instant generatedAt, LocalDate from, LocalDate to) {}
    private record Candidate(int priority, BigDecimal importance, FinancialInsightResponse insight) {}
    private record CategoryChange(TransactionCategory category, BigDecimal previous, BigDecimal delta) {}
}
