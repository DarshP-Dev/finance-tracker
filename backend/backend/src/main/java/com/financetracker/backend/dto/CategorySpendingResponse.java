package com.financetracker.backend.dto;

import com.financetracker.backend.entities.TransactionCategory;
import java.math.BigDecimal;
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
public class CategorySpendingResponse {

    private TransactionCategory category;
    private BigDecimal total;
}
