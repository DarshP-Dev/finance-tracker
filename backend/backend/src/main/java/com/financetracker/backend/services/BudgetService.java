package com.financetracker.backend.services;

import com.financetracker.backend.dto.BudgetRequest;
import com.financetracker.backend.dto.BudgetResponse;
import com.financetracker.backend.entities.Budget;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.BudgetRepository;
import com.financetracker.backend.repositories.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final Set<TransactionCategory> EXPENSE_CATEGORIES = EnumSet.of(
            TransactionCategory.HOUSING,
            TransactionCategory.UTILITIES,
            TransactionCategory.GROCERIES,
            TransactionCategory.DINING,
            TransactionCategory.TRANSPORTATION,
            TransactionCategory.HEALTHCARE,
            TransactionCategory.INSURANCE,
            TransactionCategory.DEBT_PAYMENT,
            TransactionCategory.ENTERTAINMENT,
            TransactionCategory.SHOPPING,
            TransactionCategory.EDUCATION,
            TransactionCategory.TRAVEL,
            TransactionCategory.OTHER
    );

    private final AuthenticatedUserService authenticatedUserService;
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgets(Authentication authentication, YearMonth month) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        YearMonth selectedMonth = requireMonth(month);

        return budgetRepository.findByUserIdAndMonthOrderByCategoryAsc(user.getId(), selectedMonth.atDay(1))
                .stream()
                .map(budget -> toResponse(budget, calculateAmountSpent(budget)))
                .toList();
    }

    @Transactional
    public BudgetResponse createBudget(Authentication authentication, BudgetRequest request) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        ValidatedBudget validated = validate(request);

        if (budgetRepository.existsByUserIdAndCategoryAndMonth(
                user.getId(), validated.category(), validated.month().atDay(1))) {
            throw duplicateBudget();
        }

        Budget budget = Budget.builder()
                .user(user)
                .category(validated.category())
                .monthlyLimit(validated.monthlyLimit())
                .month(validated.month().atDay(1))
                .build();

        try {
            Budget saved = budgetRepository.saveAndFlush(budget);
            return toResponse(saved, calculateAmountSpent(saved));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateBudget();
        }
    }

    @Transactional
    public BudgetResponse updateBudget(Authentication authentication, Long budgetId, BudgetRequest request) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        Budget budget = getOwnedBudget(budgetId, user.getId());
        ValidatedBudget validated = validate(request);
        LocalDate normalizedMonth = validated.month().atDay(1);

        if (budgetRepository.existsByUserIdAndCategoryAndMonthAndIdNot(
                user.getId(), validated.category(), normalizedMonth, budgetId)) {
            throw duplicateBudget();
        }

        budget.setCategory(validated.category());
        budget.setMonthlyLimit(validated.monthlyLimit());
        budget.setMonth(normalizedMonth);

        try {
            Budget saved = budgetRepository.saveAndFlush(budget);
            return toResponse(saved, calculateAmountSpent(saved));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateBudget();
        }
    }

    @Transactional
    public void deleteBudget(Authentication authentication, Long budgetId) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        budgetRepository.delete(getOwnedBudget(budgetId, user.getId()));
    }

    private Budget getOwnedBudget(Long budgetId, Long userId) {
        return budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
    }

    private ValidatedBudget validate(BudgetRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget details are required");
        }

        TransactionCategory category = normalizeCategory(request.getCategory());
        if (category == null || !EXPENSE_CATEGORIES.contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budgets are only available for expense categories");
        }

        BigDecimal monthlyLimit = request.getMonthlyLimit();
        if (monthlyLimit == null || monthlyLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monthly limit must be greater than zero");
        }

        return new ValidatedBudget(category, monthlyLimit, requireMonth(request.getMonth()));
    }

    private YearMonth requireMonth(YearMonth month) {
        if (month == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month is required in YYYY-MM format");
        }
        return month;
    }

    private TransactionCategory normalizeCategory(TransactionCategory category) {
        if (category == null) {
            return null;
        }

        return switch (category) {
            case FOOD -> TransactionCategory.DINING;
            case RENT -> TransactionCategory.HOUSING;
            case HEALTH -> TransactionCategory.HEALTHCARE;
            default -> category;
        };
    }

    private BigDecimal calculateAmountSpent(Budget budget) {
        LocalDate startDate = budget.getMonth().withDayOfMonth(1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        return transactionRepository.sumExpenseAmountByUserIdAndCategoryBetweenDates(
                budget.getUser().getId(), budget.getCategory(), startDate, endDate);
    }

    private BudgetResponse toResponse(Budget budget, BigDecimal amountSpent) {
        BigDecimal spent = amountSpent == null ? BigDecimal.ZERO : amountSpent;
        BigDecimal remaining = budget.getMonthlyLimit().subtract(spent);
        BigDecimal percentUsed = spent
                .multiply(BigDecimal.valueOf(100))
                .divide(budget.getMonthlyLimit(), 2, RoundingMode.HALF_UP);

        return BudgetResponse.builder()
                .id(budget.getId())
                .category(budget.getCategory())
                .monthlyLimit(budget.getMonthlyLimit())
                .month(YearMonth.from(budget.getMonth()))
                .amountSpent(spent)
                .remaining(remaining)
                .percentUsed(percentUsed)
                .overBudget(spent.compareTo(budget.getMonthlyLimit()) > 0)
                .createdAt(budget.getCreatedAt())
                .build();
    }

    private ResponseStatusException duplicateBudget() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A budget already exists for this category and month"
        );
    }

    private record ValidatedBudget(
            TransactionCategory category,
            BigDecimal monthlyLimit,
            YearMonth month
    ) {
    }
}
