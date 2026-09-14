package com.financetracker.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.financetracker.backend.entities.TransactionCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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
public class BudgetRequest {

    @NotNull
    private TransactionCategory category;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal monthlyLimit;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM")
    private YearMonth month;
}
