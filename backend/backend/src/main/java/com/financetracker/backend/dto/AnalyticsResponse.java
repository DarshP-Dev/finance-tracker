package com.financetracker.backend.dto;

import com.financetracker.backend.entities.TransactionCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record AnalyticsResponse(
        String period,
        LocalDate startDate,
        LocalDate endDate,
        Overview overview,
        List<CategorySpending> spendingByCategory,
        List<CashFlowMonth> incomeVsExpenses,
        Trend spendingTrend,
        BudgetAnalytics budgets,
        InvestmentAnalytics investments
) {
    public record Overview(BigDecimal totalIncome, BigDecimal totalExpenses,
                           BigDecimal netCashFlow, BigDecimal totalInvested) {}

    public record CategorySpending(TransactionCategory category, BigDecimal amount,
                                   BigDecimal percentage) {}

    public record CashFlowMonth(YearMonth month, BigDecimal income, BigDecimal expenses) {}

    public record MonthlyAmount(YearMonth month, BigDecimal amount) {}

    public record Trend(YearMonth startMonth, YearMonth endMonth,
                        List<MonthlyAmount> months) {}

    public record BudgetAnalytics(YearMonth month, BigDecimal totalBudgeted,
                                  BigDecimal budgetedSpending, BigDecimal remainingBudget,
                                  int overBudgetCategories, List<BudgetCategory> categories) {}

    public record BudgetCategory(TransactionCategory category, BigDecimal monthlyBudget,
                                 BigDecimal actualSpending, BigDecimal remaining,
                                 BigDecimal percentUsed, boolean overBudget) {}

    public record InvestmentAnalytics(BigDecimal totalInvestedAllTime, int uniqueHoldings,
                                      List<MonthlyAmount> contributions,
                                      List<InvestmentAllocation> allocation) {}

    public record InvestmentAllocation(String ticker, BigDecimal amountInvested,
                                       BigDecimal percentage) {}
}
