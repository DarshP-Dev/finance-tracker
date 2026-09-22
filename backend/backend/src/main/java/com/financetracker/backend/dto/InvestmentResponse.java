package com.financetracker.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class InvestmentResponse {

    private Long id;
    private String ticker;
    private BigDecimal shares;
    private BigDecimal purchasePrice;
    private LocalDate purchaseDate;
    private BigDecimal amountInvested;
    private LocalDateTime createdAt;
}
