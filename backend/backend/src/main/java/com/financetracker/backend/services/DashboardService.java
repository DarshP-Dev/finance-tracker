package com.financetracker.backend.services;

import com.financetracker.backend.dto.CategorySpendingResponse;
import com.financetracker.backend.dto.CashFlowTrendResponse;
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
import java.util.LinkedHashMap;
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
public class DashboardService {

    private final AuthenticatedUserService authenticatedUserService;
    private final TransactionRepository transactionRepository;
    private final InvestmentRepository investmentRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Authentication authentication, LocalDate startDate, LocalDate endDate) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        Long userId = user.getId();
        LocalDate resolvedEndDate = endDate == null ? LocalDate.now() : endDate;
        LocalDate resolvedStartDate = startDate == null ? resolvedEndDate.minusMonths(1) : startDate;

        if (resolvedStartDate.isAfter(resolvedEndDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate");
        }

        BigDecimal totalIncome = transactionRepository.sumAmountByUserIdAndTypeBetweenDates(
                userId,
                TransactionType.INCOME,
                resolvedStartDate,
                resolvedEndDate
        );
        BigDecimal totalExpenses = transactionRepository.sumAmountByUserIdAndTypeBetweenDates(
                userId,
                TransactionType.EXPENSE,
                resolvedStartDate,
                resolvedEndDate
        );
        BigDecimal investmentValue = investmentRepository.calculateInvestmentValue(userId);

        return DashboardResponse.builder()
                .summary(DashboardSummaryResponse.builder()
                        .totalIncome(totalIncome)
                        .totalExpenses(totalExpenses)
                        .totalSavings(totalIncome.subtract(totalExpenses))
                        .investmentValue(investmentValue)
                        .build())
                .categorySpending(getCategorySpending(userId, resolvedStartDate, resolvedEndDate))
                .monthlySpending(getMonthlySpending(userId, resolvedStartDate, resolvedEndDate))
                .cashFlowTrend(getCashFlowTrend(userId, resolvedStartDate, resolvedEndDate))
                .incomeVsExpenses(getIncomeVsExpenses(userId, resolvedStartDate, resolvedEndDate))
                .recentTransactions(transactionRepository.findTop5ByUserIdAndDateBetweenOrderByDateDescIdDesc(
                                userId,
                                resolvedStartDate,
                                resolvedEndDate
                        )
                        .stream()
                        .map(this::toTransactionResponse)
                        .toList())
                .build();
    }

    private List<CategorySpendingResponse> getCategorySpending(Long userId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.sumExpensesByCategoryBetweenDates(userId, startDate, endDate)
                .stream()
                .map(row -> CategorySpendingResponse.builder()
                        .category((TransactionCategory) row[0])
                        .total((BigDecimal) row[1])
                        .build())
                .toList();
    }

    private List<MonthlySpendingResponse> getMonthlySpending(Long userId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.sumDailyExpensesBetweenDates(userId, startDate, endDate)
                .stream()
                .map(row -> MonthlySpendingResponse.builder()
                        .month(toLocalDate(row[0]))
                        .total((BigDecimal) row[1])
                        .build())
                .toList();
    }

    private List<CashFlowTrendResponse> getCashFlowTrend(Long userId, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, CashFlowTrendResponse> trendByDate = new LinkedHashMap<>();

        for (Object[] row : transactionRepository.sumDailyCashFlowBetweenDates(userId, startDate, endDate)) {
            LocalDate date = toLocalDate(row[0]);
            TransactionType type = TransactionType.valueOf(String.valueOf(row[1]));
            BigDecimal total = (BigDecimal) row[2];
            CashFlowTrendResponse point = trendByDate.computeIfAbsent(date, currentDate -> CashFlowTrendResponse.builder()
                    .date(currentDate)
                    .income(BigDecimal.ZERO)
                    .expenses(BigDecimal.ZERO)
                    .build());

            if (type == TransactionType.INCOME) {
                point.setIncome(total);
            } else if (type == TransactionType.EXPENSE) {
                point.setExpenses(total);
            }
        }

        return trendByDate.values().stream().toList();
    }

    private IncomeExpenseResponse getIncomeVsExpenses(Long userId, LocalDate startDate, LocalDate endDate) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (Object[] row : transactionRepository.sumAmountByTypeBetweenDates(userId, startDate, endDate)) {
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
