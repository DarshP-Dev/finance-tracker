package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financetracker.backend.dto.BudgetRequest;
import com.financetracker.backend.dto.BudgetResponse;
import com.financetracker.backend.entities.Budget;
import com.financetracker.backend.entities.Transaction;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.BudgetRepository;
import com.financetracker.backend.repositories.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTests {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private Authentication authentication;

    private BudgetService budgetService;
    private User user;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(authenticatedUserService, budgetRepository, transactionRepository);
        user = User.builder().id(25L).email("budget@example.com").username("Budget User").build();
        when(authenticatedUserService.getCurrentUser(authentication)).thenReturn(user);
    }

    @Test
    void createsAValidBudgetForTheAuthenticatedUser() {
        BudgetRequest request = request(TransactionCategory.GROCERIES, "500.00", SEPTEMBER);
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> {
            Budget budget = invocation.getArgument(0);
            budget.setId(10L);
            return budget;
        });

        BudgetResponse response = budgetService.createBudget(authentication, request);

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getMonth()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getMonthlyLimit()).isEqualByComparingTo("500.00");
    }

    @Test
    void retrievesOnlyTheAuthenticatedUsersBudgets() {
        Budget budget = budget(12L, user, TransactionCategory.DINING, "500.00", SEPTEMBER);
        when(budgetRepository.findByUserIdAndMonthOrderByCategoryAsc(25L, LocalDate.of(2026, 9, 1)))
                .thenReturn(List.of(budget));

        List<BudgetResponse> responses = budgetService.getBudgets(authentication, SEPTEMBER);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getCategory()).isEqualTo(TransactionCategory.DINING);
        verify(budgetRepository).findByUserIdAndMonthOrderByCategoryAsc(25L, LocalDate.of(2026, 9, 1));
    }

    @Test
    void filtersBudgetsUsingTheSelectedMonthStart() {
        budgetService.getBudgets(authentication, YearMonth.of(2026, 10));

        verify(budgetRepository).findByUserIdAndMonthOrderByCategoryAsc(25L, LocalDate.of(2026, 10, 1));
    }

    @Test
    void rejectsDuplicateCategoryAndMonth() {
        BudgetRequest request = request(TransactionCategory.DINING, "500.00", SEPTEMBER);
        when(budgetRepository.existsByUserIdAndCategoryAndMonth(
                25L, TransactionCategory.DINING, LocalDate.of(2026, 9, 1))).thenReturn(true);

        assertStatusAndReason(
                () -> budgetService.createBudget(authentication, request),
                HttpStatus.CONFLICT,
                "A budget already exists for this category and month"
        );
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidMonthlyLimit() {
        assertStatusAndReason(
                () -> budgetService.createBudget(authentication, request(TransactionCategory.DINING, "0", SEPTEMBER)),
                HttpStatus.BAD_REQUEST,
                "Monthly limit must be greater than zero"
        );
    }

    @Test
    void rejectsNonExpenseCategory() {
        assertStatusAndReason(
                () -> budgetService.createBudget(authentication, request(TransactionCategory.SALARY, "500", SEPTEMBER)),
                HttpStatus.BAD_REQUEST,
                "Budgets are only available for expense categories"
        );
    }

    @Test
    void editsAnOwnedBudget() {
        Budget budget = budget(12L, user, TransactionCategory.DINING, "500", SEPTEMBER);
        when(budgetRepository.findByIdAndUserId(12L, 25L)).thenReturn(Optional.of(budget));
        when(budgetRepository.saveAndFlush(budget)).thenReturn(budget);

        BudgetResponse response = budgetService.updateBudget(
                authentication,
                12L,
                request(TransactionCategory.GROCERIES, "750", YearMonth.of(2026, 10))
        );

        assertThat(budget.getCategory()).isEqualTo(TransactionCategory.GROCERIES);
        assertThat(budget.getMonthlyLimit()).isEqualByComparingTo("750");
        assertThat(budget.getMonth()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.getMonth()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void rejectsAnUpdateThatWouldCreateADuplicate() {
        Budget budget = budget(12L, user, TransactionCategory.DINING, "500", SEPTEMBER);
        when(budgetRepository.findByIdAndUserId(12L, 25L)).thenReturn(Optional.of(budget));
        when(budgetRepository.existsByUserIdAndCategoryAndMonthAndIdNot(
                25L, TransactionCategory.GROCERIES, LocalDate.of(2026, 9, 1), 12L)).thenReturn(true);

        assertStatusAndReason(
                () -> budgetService.updateBudget(
                        authentication, 12L, request(TransactionCategory.GROCERIES, "700", SEPTEMBER)),
                HttpStatus.CONFLICT,
                "A budget already exists for this category and month"
        );
    }

    @Test
    void deletesAnOwnedBudgetWithoutTouchingTransactions() {
        Budget budget = budget(12L, user, TransactionCategory.DINING, "500", SEPTEMBER);
        when(budgetRepository.findByIdAndUserId(12L, 25L)).thenReturn(Optional.of(budget));

        budgetService.deleteBudget(authentication, 12L);

        verify(budgetRepository).delete(budget);
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    @Test
    void cannotAccessAnotherUsersBudget() {
        when(budgetRepository.findByIdAndUserId(90L, 25L)).thenReturn(Optional.empty());

        assertStatusAndReason(
                () -> budgetService.deleteBudget(authentication, 90L),
                HttpStatus.NOT_FOUND,
                "Budget not found"
        );
        verify(budgetRepository, never()).delete(any());
    }

    @Test
    void calculatesAmountSpentFromMatchingMonthlyExpenseTransactions() {
        Budget budget = budget(12L, user, TransactionCategory.DINING, "500", SEPTEMBER);
        when(budgetRepository.findByUserIdAndMonthOrderByCategoryAsc(25L, LocalDate.of(2026, 9, 1)))
                .thenReturn(List.of(budget));
        when(transactionRepository.sumExpenseAmountByUserIdAndCategoryBetweenDates(
                25L, TransactionCategory.DINING, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(new BigDecimal("325"));

        BudgetResponse response = budgetService.getBudgets(authentication, SEPTEMBER).getFirst();

        assertThat(response.getAmountSpent()).isEqualByComparingTo("325");
    }

    @Test
    void calculatesRemainingAmount() {
        BudgetResponse response = responseForSpending("500", "325");

        assertThat(response.getRemaining()).isEqualByComparingTo("175");
        assertThat(response.getPercentUsed()).isEqualByComparingTo("65.00");
        assertThat(response.isOverBudget()).isFalse();
    }

    @Test
    void calculatesUncappedPercentageAndOverBudgetState() {
        BudgetResponse response = responseForSpending("500", "650");

        assertThat(response.getRemaining()).isEqualByComparingTo("-150");
        assertThat(response.getPercentUsed()).isEqualByComparingTo("130.00");
        assertThat(response.isOverBudget()).isTrue();
    }

    @Test
    void normalizesLegacyExpenseCategoryAliases() {
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetResponse response = budgetService.createBudget(
                authentication,
                request(TransactionCategory.FOOD, "500", SEPTEMBER)
        );

        assertThat(response.getCategory()).isEqualTo(TransactionCategory.DINING);
    }

    private BudgetResponse responseForSpending(String limit, String spent) {
        Budget budget = budget(12L, user, TransactionCategory.DINING, limit, SEPTEMBER);
        when(budgetRepository.findByUserIdAndMonthOrderByCategoryAsc(25L, LocalDate.of(2026, 9, 1)))
                .thenReturn(List.of(budget));
        when(transactionRepository.sumExpenseAmountByUserIdAndCategoryBetweenDates(
                25L, TransactionCategory.DINING, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(new BigDecimal(spent));
        return budgetService.getBudgets(authentication, SEPTEMBER).getFirst();
    }

    private BudgetRequest request(TransactionCategory category, String limit, YearMonth month) {
        return BudgetRequest.builder()
                .category(category)
                .monthlyLimit(new BigDecimal(limit))
                .month(month)
                .build();
    }

    private Budget budget(Long id, User owner, TransactionCategory category, String limit, YearMonth month) {
        return Budget.builder()
                .id(id)
                .user(owner)
                .category(category)
                .monthlyLimit(new BigDecimal(limit))
                .month(month.atDay(1))
                .build();
    }

    private void assertStatusAndReason(Runnable action, HttpStatus status, String reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(status);
                    assertThat(exception.getReason()).isEqualTo(reason);
                });
    }
}
