package com.financetracker.backend.services;

import com.financetracker.backend.dto.CategorySpendingResponse;
import com.financetracker.backend.dto.DashboardResponse;
import com.financetracker.backend.dto.DashboardSummaryResponse;
import com.financetracker.backend.dto.IncomeExpenseResponse;
import com.financetracker.backend.dto.MonthlySpendingResponse;
import com.financetracker.backend.dto.TransactionResponse;
import com.financetracker.backend.entities.Transaction;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.InvestmentRepository;
import com.financetracker.backend.repositories.TransactionRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AuthenticatedUserService authenticatedUserService;
    private final TransactionRepository transactionRepository;
    private final InvestmentRepository investmentRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Authentication authentication) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        Long userId = user.getId();

        BigDecimal totalIncome = transactionRepository.sumAmountByUserIdAndType(userId, TransactionType.INCOME);
        BigDecimal totalExpenses = transactionRepository.sumAmountByUserIdAndType(userId, TransactionType.EXPENSE);
        BigDecimal investmentValue = investmentRepository.calculateInvestmentValue(userId);

        return DashboardResponse.builder()
                .summary(DashboardSummaryResponse.builder()
                        .totalIncome(totalIncome)
                        .totalExpenses(totalExpenses)
                        .totalSavings(totalIncome.subtract(totalExpenses))
                        .investmentValue(investmentValue)
                        .build())
                .categorySpending(getCategorySpending(userId))
                .monthlySpending(getMonthlySpending(userId))
                .incomeVsExpenses(getIncomeVsExpenses(userId))
                .recentTransactions(transactionRepository.findTop5ByUserIdOrderByDateDescIdDesc(userId)
                        .stream()
                        .map(this::toTransactionResponse)
                        .toList())
                .build();
    }

    private List<CategorySpendingResponse> getCategorySpending(Long userId) {
        return transactionRepository.sumExpensesByCategory(userId)
                .stream()
                .map(row -> CategorySpendingResponse.builder()
                        .category((TransactionCategory) row[0])
                        .total((BigDecimal) row[1])
                        .build())
                .toList();
    }

    private List<MonthlySpendingResponse> getMonthlySpending(Long userId) {
        return transactionRepository.sumMonthlyExpenses(userId)
                .stream()
                .map(row -> MonthlySpendingResponse.builder()
                        .month(toLocalDate(row[0]))
                        .total((BigDecimal) row[1])
                        .build())
                .toList();
    }

    private IncomeExpenseResponse getIncomeVsExpenses(Long userId) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (Object[] row : transactionRepository.sumAmountByType(userId)) {
            TransactionType type = (TransactionType) row[0];
            BigDecimal total = (BigDecimal) row[1];

            if (type == TransactionType.INCOME) {
                income = total;
            } else if (type == TransactionType.EXPENSE) {
                expenses = total;
            }
        }

        return IncomeExpenseResponse.builder()
                .income(income)
                .expenses(expenses)
                .build();
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value instanceof Date date) {
            return date.toLocalDate();
        }

        throw new IllegalStateException("Unsupported month value returned from database");
    }

    private TransactionResponse toTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .category(transaction.getCategory())
                .type(transaction.getType())
                .description(transaction.getDescription())
                .date(transaction.getDate())
                .merchant(transaction.getMerchant())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
