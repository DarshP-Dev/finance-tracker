package com.financetracker.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.financetracker.backend.entities.TransactionCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
public class BudgetResponse {

    private Long id;
    private TransactionCategory category;
    private BigDecimal monthlyLimit;

    @JsonFormat(pattern = "yyyy-MM")
    private YearMonth month;

    private BigDecimal amountSpent;
    private BigDecimal remaining;
    private BigDecimal percentUsed;
    private boolean overBudget;
    private LocalDateTime createdAt;
}
