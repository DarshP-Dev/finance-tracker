package com.financetracker.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
public class CashFlowTrendResponse {

    private LocalDate date;
    private BigDecimal income;
    private BigDecimal expenses;
}
