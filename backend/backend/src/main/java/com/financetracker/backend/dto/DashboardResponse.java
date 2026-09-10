package com.financetracker.backend.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private DashboardSummaryResponse summary;
    private List<CategorySpendingResponse> categorySpending;
    private List<MonthlySpendingResponse> monthlySpending;
    private List<CashFlowTrendResponse> cashFlowTrend;
    private IncomeExpenseResponse incomeVsExpenses;
    private List<TransactionResponse> recentTransactions;
}
