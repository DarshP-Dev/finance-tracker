package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financetracker.backend.dto.AnalyticsResponse;
import com.financetracker.backend.entities.Budget;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.BudgetRepository;
import com.financetracker.backend.repositories.InvestmentRepository;
import com.financetracker.backend.repositories.TransactionRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTests {
    @Mock AuthenticatedUserService users;
    @Mock TransactionRepository transactions;
    @Mock BudgetRepository budgets;
    @Mock InvestmentRepository investments;
    @Mock Authentication authentication;
    AnalyticsService service;
    static final Long USER_ID = 42L;
    static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(users, transactions, budgets, investments);
        when(users.getCurrentUser(authentication)).thenReturn(User.builder().id(USER_ID).build());
    }

    @Test
    void calculatesIncomeExpenseNetAndPeriodInvestmentCostPrecisely() {
        when(transactions.sumAmountByUserIdAndTypeBetweenDates(USER_ID, TransactionType.INCOME,
                LocalDate.of(2026, 9, 1), TODAY)).thenReturn(new BigDecimal("3000.10"));
        when(transactions.sumAmountByUserIdAndTypeBetweenDates(USER_ID, TransactionType.EXPENSE,
                LocalDate.of(2026, 9, 1), TODAY)).thenReturn(new BigDecimal("1800.05"));
        when(investments.sumCostBetweenDates(USER_ID, LocalDate.of(2026, 9, 1), TODAY))
                .thenReturn(new BigDecimal("250.125000"));
        when(transactions.sumExpensesByCategoryBetweenDates(USER_ID, LocalDate.of(2026, 9, 1), TODAY))
                .thenReturn(List.<Object[]>of(new Object[] {TransactionCategory.DINING, new BigDecimal("300.01")}));

        AnalyticsResponse result = service.getAnalytics(authentication, AnalyticsService.Period.THIS_MONTH, TODAY);

        assertThat(result.overview().netCashFlow()).isEqualByComparingTo("1200.05");
        assertThat(result.overview().totalInvested()).isEqualByComparingTo("250.125000");
        assertThat(result.spendingByCategory().getFirst().percentage()).isEqualByComparingTo("16.67");
        verify(investments).sumCostBetweenDates(USER_ID, LocalDate.of(2026, 9, 1), TODAY);
    }

    @Test
    void groupsCashFlowAndContributionsByMonthWithZeroMonths() {
        when(transactions.sumMonthlyCashFlowBetweenDates(USER_ID, LocalDate.of(2026, 7, 1), TODAY))
                .thenReturn(List.of(new Object[] {Date.valueOf("2026-07-01"), "INCOME", new BigDecimal("3000")},
                        new Object[] {Date.valueOf("2026-09-01"), "EXPENSE", new BigDecimal("2100")}));
        when(investments.sumMonthlyCostBetweenDates(USER_ID, LocalDate.of(2026, 7, 1), TODAY))
                .thenReturn(List.<Object[]>of(new Object[] {Date.valueOf("2026-08-01"), new BigDecimal("12.345678")}));

        AnalyticsResponse result = service.getAnalytics(authentication, AnalyticsService.Period.LAST_3_MONTHS, TODAY);

        assertThat(result.incomeVsExpenses()).hasSize(3);
        assertThat(result.incomeVsExpenses().get(1).income()).isEqualByComparingTo("0");
        assertThat(result.incomeVsExpenses().get(2).expenses()).isEqualByComparingTo("2100");
        assertThat(result.investments().contributions().get(1).amount()).isEqualByComparingTo("12.345678");
    }

    @Test
    void calculatesBudgetOnlyForBudgetedCategoriesUsingTheSelectedMonth() {
        LocalDate august = LocalDate.of(2026, 8, 1);
        when(budgets.findByUserIdAndMonthOrderByCategoryAsc(USER_ID, august)).thenReturn(List.of(
                Budget.builder().category(TransactionCategory.DINING).monthlyLimit(new BigDecimal("500")).build(),
                Budget.builder().category(TransactionCategory.ENTERTAINMENT).monthlyLimit(new BigDecimal("200")).build()));
        when(transactions.sumExpensesByCategoryBetweenDates(USER_ID, august, august.withDayOfMonth(31)))
                .thenReturn(List.of(new Object[] {TransactionCategory.DINING, new BigDecimal("325")},
                        new Object[] {TransactionCategory.ENTERTAINMENT, new BigDecimal("260")},
                        new Object[] {TransactionCategory.SHOPPING, new BigDecimal("50")}));

        AnalyticsResponse result = service.getAnalytics(authentication, AnalyticsService.Period.LAST_MONTH, TODAY);

        assertThat(result.startDate()).isEqualTo(august);
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(result.budgets().month()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(result.budgets().totalBudgeted()).isEqualByComparingTo("700");
        assertThat(result.budgets().budgetedSpending()).isEqualByComparingTo("585");
        assertThat(result.budgets().remainingBudget()).isEqualByComparingTo("115");
        assertThat(result.budgets().overBudgetCategories()).isEqualTo(1);
        assertThat(result.budgets().categories().getFirst().percentUsed()).isEqualByComparingTo("65");
        assertThat(result.budgets().categories().get(1).overBudget()).isTrue();
        verify(budgets).findByUserIdAndMonthOrderByCategoryAsc(USER_ID, august);
    }

    @Test
    void calculatesAllTimeAllocationFromAuthenticatedUsersTickerAggregates() {
        when(investments.sumCostByTicker(USER_ID)).thenReturn(List.of(
                new Object[] {"AAPL", new BigDecimal("2.775")},
                new Object[] {"VOO", new BigDecimal("1.225")}));

        AnalyticsResponse result = service.getAnalytics(authentication, AnalyticsService.Period.THIS_MONTH, TODAY);

        assertThat(result.investments().totalInvestedAllTime()).isEqualByComparingTo("4.000");
        assertThat(result.investments().uniqueHoldings()).isEqualTo(2);
        assertThat(result.investments().allocation().getFirst().percentage()).isEqualByComparingTo("69.38");
        assertThat(result.investments().allocation().get(1).percentage()).isEqualByComparingTo("30.63");
    }

    @Test
    void returnsZeroTotalsAndEmptyGroupsWithoutData() {
        AnalyticsResponse result = service.getAnalytics(authentication, AnalyticsService.Period.THIS_MONTH, TODAY);

        assertThat(result.overview().netCashFlow()).isEqualByComparingTo("0");
        assertThat(result.spendingByCategory()).isEmpty();
        assertThat(result.budgets().categories()).isEmpty();
        assertThat(result.investments().allocation()).isEmpty();
        assertThat(result.spendingTrend().months()).hasSize(6);
        assertThat(result.spendingTrend().months()).allMatch(month -> month.amount().signum() == 0);
    }

    @Test
    void customDateRangeDrivesPeriodAggregatesAndUsesEndMonthForBudgets() {
        LocalDate start = LocalDate.of(2025, 11, 14);
        LocalDate end = LocalDate.of(2026, 2, 3);

        AnalyticsResponse result = service.getAnalytics(authentication, AnalyticsService.Period.CUSTOM,
                start, end, TODAY);

        assertThat(result.period()).isEqualTo("CUSTOM");
        assertThat(result.startDate()).isEqualTo(start);
        assertThat(result.endDate()).isEqualTo(end);
        assertThat(result.incomeVsExpenses()).hasSize(4);
        assertThat(result.investments().contributions()).hasSize(4);
        assertThat(result.budgets().month()).isEqualTo(YearMonth.of(2026, 2));
        verify(transactions).sumAmountByUserIdAndTypeBetweenDates(USER_ID,
                TransactionType.INCOME, start, end);
        verify(investments).sumCostBetweenDates(USER_ID, start, end);
        verify(budgets).findByUserIdAndMonthOrderByCategoryAsc(USER_ID, LocalDate.of(2026, 2, 1));
    }

    @Test
    void rejectsIncompleteOrReversedCustomDates() {
        assertThatThrownBy(() -> service.getAnalytics(authentication, AnalyticsService.Period.CUSTOM,
                LocalDate.of(2026, 1, 1), null, TODAY))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.getAnalytics(authentication, AnalyticsService.Period.CUSTOM,
                TODAY, TODAY.minusDays(1), TODAY))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
