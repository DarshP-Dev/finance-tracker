package com.financetracker.backend.services;

import com.financetracker.backend.dto.AnalyticsResponse;
import com.financetracker.backend.entities.Budget;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import com.financetracker.backend.repositories.BudgetRepository;
import com.financetracker.backend.repositories.InvestmentRepository;
import com.financetracker.backend.repositories.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    public enum Period { THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, LAST_6_MONTHS, THIS_YEAR, CUSTOM }

    private final AuthenticatedUserService authenticatedUserService;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final InvestmentRepository investmentRepository;

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(Authentication authentication, Period period) {
        return getAnalytics(authentication, period, null, null);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(Authentication authentication, Period period,
            LocalDate startDate, LocalDate endDate) {
        return getAnalytics(authentication, period, startDate, endDate, LocalDate.now());
    }

    AnalyticsResponse getAnalytics(Authentication authentication, Period period, LocalDate today) {
        return getAnalytics(authentication, period, null, null, today);
    }

    AnalyticsResponse getAnalytics(Authentication authentication, Period period,
            LocalDate startDate, LocalDate endDate, LocalDate today) {
        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        if (period == Period.CUSTOM) {
            if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Custom analytics requires a valid startDate and endDate");
            }
        } else if (startDate != null || endDate != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dates may only be supplied with the CUSTOM period");
        }
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate start = switch (period) {
            case THIS_MONTH -> currentMonth.atDay(1);
            case LAST_MONTH -> currentMonth.minusMonths(1).atDay(1);
            case LAST_3_MONTHS -> currentMonth.minusMonths(2).atDay(1);
            case LAST_6_MONTHS -> currentMonth.minusMonths(5).atDay(1);
            case THIS_YEAR -> today.withDayOfYear(1);
            case CUSTOM -> startDate;
        };
        LocalDate end = period == Period.CUSTOM ? endDate
                : period == Period.LAST_MONTH ? currentMonth.minusMonths(1).atEndOfMonth() : today;

        BigDecimal income = zero(transactionRepository.sumAmountByUserIdAndTypeBetweenDates(
                userId, TransactionType.INCOME, start, end));
        BigDecimal expenses = zero(transactionRepository.sumAmountByUserIdAndTypeBetweenDates(
                userId, TransactionType.EXPENSE, start, end));
        BigDecimal invested = zero(investmentRepository.sumCostBetweenDates(userId, start, end));

        List<AnalyticsResponse.CategorySpending> categories = transactionRepository
                .sumExpensesByCategoryBetweenDates(userId, start, end).stream()
                .map(row -> new AnalyticsResponse.CategorySpending((TransactionCategory) row[0],
                        (BigDecimal) row[1], percentage((BigDecimal) row[1], expenses)))
                .toList();

        Map<YearMonth, BigDecimal[]> cashFlow = new HashMap<>();
        for (Object[] row : transactionRepository.sumMonthlyCashFlowBetweenDates(userId, start, end)) {
            YearMonth month = YearMonth.from(toLocalDate(row[0]));
            BigDecimal[] totals = cashFlow.computeIfAbsent(month,
                    ignored -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            if (TransactionType.INCOME.name().equals(String.valueOf(row[1]))) {
                totals[0] = (BigDecimal) row[2];
            } else {
                totals[1] = (BigDecimal) row[2];
            }
        }
        List<AnalyticsResponse.CashFlowMonth> incomeVsExpenses = new ArrayList<>();
        for (YearMonth month = YearMonth.from(start); !month.isAfter(YearMonth.from(end)); month = month.plusMonths(1)) {
            BigDecimal[] amounts = cashFlow.getOrDefault(month,
                    new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            incomeVsExpenses.add(new AnalyticsResponse.CashFlowMonth(month, amounts[0], amounts[1]));
        }

        YearMonth trendStart = currentMonth.minusMonths(5);
        AnalyticsResponse.Trend trend = new AnalyticsResponse.Trend(trendStart, currentMonth,
                monthlyAmounts(transactionRepository.sumMonthlyExpensesBetweenDates(
                        userId, trendStart.atDay(1), today), trendStart, currentMonth));

        YearMonth budgetMonth = YearMonth.from(end);
        List<Budget> budgets = budgetRepository.findByUserIdAndMonthOrderByCategoryAsc(
                userId, budgetMonth.atDay(1));
        Map<TransactionCategory, BigDecimal> budgetMonthSpending = new HashMap<>();
        if (!budgets.isEmpty()) {
            for (Object[] row : transactionRepository.sumExpensesByCategoryBetweenDates(
                    userId, budgetMonth.atDay(1), budgetMonth.atEndOfMonth())) {
                budgetMonthSpending.put((TransactionCategory) row[0], (BigDecimal) row[1]);
            }
        }
        BigDecimal totalBudgeted = BigDecimal.ZERO;
        BigDecimal budgetedSpending = BigDecimal.ZERO;
        int overBudgetCount = 0;
        List<AnalyticsResponse.BudgetCategory> budgetCategories = new ArrayList<>();
        for (Budget budget : budgets) {
            BigDecimal limit = budget.getMonthlyLimit();
            BigDecimal spent = budgetMonthSpending.getOrDefault(budget.getCategory(), BigDecimal.ZERO);
            BigDecimal remaining = limit.subtract(spent);
            boolean overBudget = remaining.signum() < 0;
            totalBudgeted = totalBudgeted.add(limit);
            budgetedSpending = budgetedSpending.add(spent);
            if (overBudget) overBudgetCount++;
            budgetCategories.add(new AnalyticsResponse.BudgetCategory(budget.getCategory(), limit,
                    spent, remaining, percentage(spent, limit), overBudget));
        }
        AnalyticsResponse.BudgetAnalytics budgetAnalytics = new AnalyticsResponse.BudgetAnalytics(
                budgetMonth, totalBudgeted, budgetedSpending,
                totalBudgeted.subtract(budgetedSpending), overBudgetCount, budgetCategories);

        List<Object[]> tickerRows = investmentRepository.sumCostByTicker(userId);
        BigDecimal allTimeInvested = tickerRows.stream().map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AnalyticsResponse.InvestmentAllocation> allocation = tickerRows.stream()
                .map(row -> new AnalyticsResponse.InvestmentAllocation((String) row[0],
                        (BigDecimal) row[1], percentage((BigDecimal) row[1], allTimeInvested)))
                .toList();
        AnalyticsResponse.InvestmentAnalytics investmentAnalytics = new AnalyticsResponse.InvestmentAnalytics(
                allTimeInvested, tickerRows.size(), monthlyAmounts(
                        investmentRepository.sumMonthlyCostBetweenDates(userId, start, end),
                        YearMonth.from(start), YearMonth.from(end)), allocation);

        return new AnalyticsResponse(period.name(), start, end,
                new AnalyticsResponse.Overview(income, expenses, income.subtract(expenses), invested),
                categories, incomeVsExpenses, trend, budgetAnalytics, investmentAnalytics);
    }

    private List<AnalyticsResponse.MonthlyAmount> monthlyAmounts(List<Object[]> rows,
            YearMonth start, YearMonth end) {
        Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
        for (Object[] row : rows) {
            byMonth.put(YearMonth.from(toLocalDate(row[0])), (BigDecimal) row[1]);
        }
        List<AnalyticsResponse.MonthlyAmount> result = new ArrayList<>();
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            result.add(new AnalyticsResponse.MonthlyAmount(month,
                    byMonth.getOrDefault(month, BigDecimal.ZERO)));
        }
        return result;
    }

    private BigDecimal percentage(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO : part.multiply(BigDecimal.valueOf(100))
                .divide(whole, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date date) return date.toLocalDate();
        throw new IllegalStateException("Unsupported month value returned from database");
    }
}
