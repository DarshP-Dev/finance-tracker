package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.financetracker.backend.dto.FinancialInsightResponse;
import com.financetracker.backend.dto.RecurringForecastResponse;
import com.financetracker.backend.dto.UpcomingRecurringTransactionResponse;
import com.financetracker.backend.entities.*;
import com.financetracker.backend.repositories.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class FinancialInsightsServiceTests {
    private final AuthenticatedUserService users = mock(AuthenticatedUserService.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final BudgetRepository budgets = mock(BudgetRepository.class);
    private final InvestmentRepository investments = mock(InvestmentRepository.class);
    private final RecurringForecastService recurring = mock(RecurringForecastService.class);
    private final LocalDate today = LocalDate.of(2026, 10, 15);
    private final LocalDate start = today.withDayOfMonth(1);
    private final LocalDate oldStart = start.minusMonths(1);
    private final LocalDate oldEnd = start.minusDays(1);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T16:00:00Z"), ZoneId.of("America/Toronto"));
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("owner@example.com", null);
    private FinancialInsightsService service;

    @BeforeEach
    void setUp() {
        when(users.getCurrentUser(auth)).thenReturn(User.builder().id(7L).build());
        when(recurring.getForecastWithUpcoming(auth, today, today.plusDays(29)))
                .thenReturn(new RecurringForecastService.ForecastWithUpcoming(
                        new RecurringForecastResponse(today, today.plusDays(29), BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0), List.of()));
        service = new FinancialInsightsService(new AnalyticsService(users, transactions, budgets, investments), recurring, clock);
    }

    @Test void spendingIncrease() {
        totals("0", "118", "0", "100");
        assertThat(insight("spending-change").metricValue()).isEqualByComparingTo("18");
        assertThat(insight("spending-change").message()).contains("18% higher", "month to date", "full previous");
    }
    @Test void spendingDecrease() {
        totals("0", "88", "0", "100");
        assertThat(insight("spending-change").severity()).isEqualTo(FinancialInsightResponse.Severity.POSITIVE);
        assertThat(insight("spending-change").message()).contains("12% lower");
    }
    @Test void zeroPreviousExpensesSkipsPercentage() {
        totals("0", "100", "0", "0");
        assertThat(keys()).doesNotContain("spending-change");
    }
    @Test void tinyOverallChangesAreIgnored() {
        totals("0", "109", "0", "100");
        assertThat(keys()).doesNotContain("spending-change");
    }
    @Test void categoryIncrease() {
        categories(TransactionCategory.DINING, "248", "200");
        assertThat(insight("category-dining").message()).contains("24% higher");
    }
    @Test void categoryDecreaseIncludingAbsentCurrentCategory() {
        when(transactions.sumExpensesByCategoryBetweenDates(7L, oldStart, oldEnd))
                .thenReturn(List.<Object[]>of(new Object[]{TransactionCategory.DINING, new BigDecimal("100")}));
        assertThat(insight("category-dining").metricValue()).isEqualByComparingTo("-100");
    }
    @Test void tinyCategoryBaselineIsIgnored() {
        categories(TransactionCategory.DINING, "3", "1");
        assertThat(keys()).doesNotContain("category-dining");
    }
    @Test void tinyAbsoluteCategoryChangeIsIgnored() {
        categories(TransactionCategory.DINING, "60", "50");
        assertThat(keys()).doesNotContain("category-dining");
    }
    @Test void budgetAt80PercentWarnsAndRemainingIsReused() {
        budget("100", "80");
        var insight = insight("budget-dining");
        assertThat(insight.severity()).isEqualTo(FinancialInsightResponse.Severity.WARNING);
        assertThat(insight.metricValue()).isEqualByComparingTo("80");
        assertThat(insight.comparisonValue()).isEqualByComparingTo("20");
    }
    @Test void exceededBudgetWarnsWithExactExcess() {
        budget("100", "142");
        assertThat(insight("budget-dining").message()).contains("exceeded", "$42.00");
        assertThat(insight("budget-dining").comparisonValue()).isEqualByComparingTo("-42");
    }
    @Test void exactly100PercentIsUsedNotExceeded() {
        budget("100", "100");
        assertThat(insight("budget-dining").message()).contains("used 100%").doesNotContain("exceeded");
    }
    @Test void budgetAheadOfPaceWarnsWithoutPrediction() {
        budget("100", "75");
        assertThat(insight("budget-dining").message()).contains("48.4% complete", "ahead of time-based pace")
                .doesNotContain("will");
    }
    @Test void lowBudgetUsageIsIgnored() {
        budget("100", "20");
        assertThat(keys()).doesNotContain("budget-dining");
    }
    @Test void savingsRateIsPrecise() {
        totals("1000", "730", "0", "0");
        assertThat(insight("savings-rate").metricValue()).isEqualByComparingTo("27");
        assertThat(insight("savings-rate").message()).contains("$270.00", "27%");
    }
    @Test void savingsComparison() {
        totals("1000", "730", "1000", "790");
        var insight = insight("savings-rate");
        assertThat(insight.comparisonValue()).isEqualByComparingTo("21");
        assertThat(insight.message()).contains("increased from 21%", "27%");
    }
    @Test void decliningSavingsWarns() {
        totals("1000", "790", "1000", "730");
        assertThat(insight("savings-rate").severity()).isEqualTo(FinancialInsightResponse.Severity.WARNING);
    }
    @Test void zeroIncomeDoesNotDivide() {
        totals("0", "315", "0", "0");
        assertThat(keys()).doesNotContain("savings-rate");
        assertThat(insight("income-expenses").message()).contains("$315.00");
    }
    @Test void negativeSavingsAndCashFlowAreHandledWithoutDuplicateWarnings() {
        totals("1000", "1315", "0", "0");
        var insight = insight("income-expenses");
        assertThat(insight.message()).contains("$315.00", "-31.5%");
        assertThat(insight.comparisonValue()).isEqualByComparingTo("-31.5");
        assertThat(keys()).doesNotContain("savings-rate");
    }
    @Test void recurringUsesExistingForecastAndExact30DayRange() {
        forecast("2400", "1184", List.of());
        assertThat(insight("recurring-summary").message()).contains("$2400.00", "$1184.00", "30 days");
        verify(recurring, atLeastOnce()).getForecastWithUpcoming(auth, today, today.plusDays(29));
    }
    @Test void recurringNetCashFlowIsReused() {
        forecast("2400", "1184", List.of());
        assertThat(insight("recurring-summary").metricValue()).isEqualByComparingTo("1216");
        assertThat(insight("recurring-summary").message()).contains("$1216.00", "excludes other");
    }
    @Test void recurringNegativeNetIsClearlyLimitedToKnownSchedules() {
        forecast("0", "340", List.of());
        assertThat(insight("recurring-summary").message()).contains("Known recurring expenses exceed income", "$340.00");
    }
    @Test void largestUpcomingExpenseInSevenDaysIsSelected() {
        forecast("0", "1100", List.of(occurrence(1L, "850", 4), occurrence(2L, "100", 2), occurrence(3L, "2000", 7)));
        assertThat(insight("upcoming-largest-expense").message()).contains("$850.00", "in 4 days");
    }
    @Test void noRecurringIsHandled() {
        assertThat(result()).noneMatch(i -> i.type() == FinancialInsightResponse.Type.RECURRING
                || i.type() == FinancialInsightResponse.Type.FORECAST);
    }
    @Test void noBudgetsIsHandled() {
        assertThat(result()).noneMatch(i -> i.type() == FinancialInsightResponse.Type.BUDGET);
    }
    @Test void newUserReturnsEmptyList() { assertThat(result()).isEmpty(); }
    @Test void budgetCategoryWarningSuppressesEquivalentCategoryWarning() {
        budget("100", "90");
        categories(TransactionCategory.DINING, "90", "50");
        assertThat(keys()).contains("budget-dining").doesNotContain("category-dining");
    }
    @Test void singleCategoryChangeSuppressesEquivalentOverallChange() {
        totals("0", "248", "0", "200");
        categories(TransactionCategory.DINING, "248", "200");
        assertThat(keys()).contains("category-dining").doesNotContain("spending-change");
    }
    @Test void atMostThreeCategoryChangesAreSelectedByAbsoluteAmount() {
        var selected = List.of(TransactionCategory.DINING, TransactionCategory.TRAVEL,
                TransactionCategory.SHOPPING, TransactionCategory.ENTERTAINMENT);
        when(transactions.sumExpensesByCategoryBetweenDates(7L, oldStart, oldEnd))
                .thenReturn(selected.stream().map(c -> new Object[]{c, new BigDecimal("100")}).toList());
        when(transactions.sumExpensesByCategoryBetweenDates(7L, start, today)).thenReturn(List.of(
                new Object[]{selected.get(0), new BigDecimal("125")},
                new Object[]{selected.get(1), new BigDecimal("200")},
                new Object[]{selected.get(2), new BigDecimal("300")},
                new Object[]{selected.get(3), new BigDecimal("400")}));
        assertThat(result()).filteredOn(i -> i.category() != null).hasSize(3);
        assertThat(keys()).doesNotContain("category-dining");
    }
    @Test void curatedResultHasStableOrderUniqueKeysAndMaximumEight() {
        totals("1000", "1800", "1000", "1000");
        var categories = List.of(TransactionCategory.DINING, TransactionCategory.HOUSING,
                TransactionCategory.SHOPPING, TransactionCategory.TRAVEL, TransactionCategory.GROCERIES,
                TransactionCategory.ENTERTAINMENT);
        when(transactions.sumExpensesByCategoryBetweenDates(7L, start, today))
                .thenReturn(categories.stream().map(c -> new Object[]{c, new BigDecimal("300")}).toList());
        when(transactions.sumExpensesByCategoryBetweenDates(7L, oldStart, oldEnd))
                .thenReturn(categories.stream().map(c -> new Object[]{c, new BigDecimal("100")}).toList());
        when(budgets.findByUserIdAndMonthOrderByCategoryAsc(7L, start)).thenReturn(categories.subList(0, 3).stream()
                .map(c -> Budget.builder().category(c).monthlyLimit(new BigDecimal("200")).build()).toList());
        when(transactions.sumExpensesByCategoryBetweenDates(7L, start, start.withDayOfMonth(31)))
                .thenReturn(categories.stream().map(c -> new Object[]{c, new BigDecimal("300")}).toList());
        forecast("0", "850", List.of(occurrence(1L, "850", 4)));
        assertThat(result()).hasSize(8);
        assertThat(result()).extracting(FinancialInsightResponse::key).doesNotHaveDuplicates();
        assertThat(result().subList(0, 3)).allMatch(i -> i.type() == FinancialInsightResponse.Type.BUDGET);
        assertThat(service.generateInsightsForUser(auth)).isEqualTo(service.generateInsightsForUser(auth));
    }
    @Test void authenticatedIdentityFlowsToEveryDataSourceWithoutWrites() {
        result();
        verify(transactions).sumAmountByUserIdAndTypeBetweenDates(7L, TransactionType.EXPENSE, start, today);
        verify(budgets).findByUserIdAndMonthOrderByCategoryAsc(7L, start);
        verify(investments, times(2)).sumCostByTicker(7L);
        verify(recurring).getForecastWithUpcoming(auth, today, today.plusDays(29));
        assertThat(mockingDetails(transactions).getInvocations()).allMatch(i -> i.getMethod().getName().startsWith("sum"));
        assertThat(mockingDetails(budgets).getInvocations()).allMatch(i -> i.getMethod().getName().startsWith("find"));
    }
    @Test void investmentsOnlyDescribeRecordedPurchaseCost() {
        when(investments.sumCostByTicker(7L)).thenReturn(List.<Object[]>of(new Object[]{"ABC", new BigDecimal("5400")}));
        assertThat(insight("recorded-investments").message()).contains("1 investment position", "$5400.00", "not current market value");
    }

    private List<FinancialInsightResponse> result() { return service.generateInsightsForUser(auth).insights(); }
    private List<String> keys() { return result().stream().map(FinancialInsightResponse::key).toList(); }
    private FinancialInsightResponse insight(String key) {
        return result().stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }
    private void totals(String income, String expense, String oldIncome, String oldExpense) {
        when(transactions.sumAmountByUserIdAndTypeBetweenDates(7L, TransactionType.INCOME, start, today)).thenReturn(new BigDecimal(income));
        when(transactions.sumAmountByUserIdAndTypeBetweenDates(7L, TransactionType.EXPENSE, start, today)).thenReturn(new BigDecimal(expense));
        when(transactions.sumAmountByUserIdAndTypeBetweenDates(7L, TransactionType.INCOME, oldStart, oldEnd)).thenReturn(new BigDecimal(oldIncome));
        when(transactions.sumAmountByUserIdAndTypeBetweenDates(7L, TransactionType.EXPENSE, oldStart, oldEnd)).thenReturn(new BigDecimal(oldExpense));
    }
    private void categories(TransactionCategory category, String current, String previous) {
        when(transactions.sumExpensesByCategoryBetweenDates(7L, start, today))
                .thenReturn(List.<Object[]>of(new Object[]{category, new BigDecimal(current)}));
        when(transactions.sumExpensesByCategoryBetweenDates(7L, oldStart, oldEnd))
                .thenReturn(List.<Object[]>of(new Object[]{category, new BigDecimal(previous)}));
    }
    private void budget(String limit, String spent) {
        when(budgets.findByUserIdAndMonthOrderByCategoryAsc(7L, start)).thenReturn(List.of(
                Budget.builder().category(TransactionCategory.DINING).monthlyLimit(new BigDecimal(limit)).build()));
        when(transactions.sumExpensesByCategoryBetweenDates(7L, start, start.withDayOfMonth(31)))
                .thenReturn(List.<Object[]>of(new Object[]{TransactionCategory.DINING, new BigDecimal(spent)}));
    }
    private void forecast(String income, String expense, List<UpcomingRecurringTransactionResponse> upcoming) {
        BigDecimal i = new BigDecimal(income), e = new BigDecimal(expense);
        when(recurring.getForecastWithUpcoming(auth, today, today.plusDays(29)))
                .thenReturn(new RecurringForecastService.ForecastWithUpcoming(new RecurringForecastResponse(today,
                        today.plusDays(29), i, e, i.subtract(e), i.signum() > 0 ? 1 : 0, e.signum() > 0 ? 1 : 0), upcoming));
    }
    private UpcomingRecurringTransactionResponse occurrence(Long id, String amount, int days) {
        return new UpcomingRecurringTransactionResponse(id, "Rent", "Landlord", new BigDecimal(amount),
                TransactionCategory.HOUSING, TransactionType.EXPENSE, RecurringFrequency.MONTHLY, today.plusDays(days));
    }
}
