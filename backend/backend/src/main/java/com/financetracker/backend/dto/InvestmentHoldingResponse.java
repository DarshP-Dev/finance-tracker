package com.financetracker.backend.dto;

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
public class InvestmentHoldingResponse {

    private String ticker;
    private BigDecimal totalShares;
    private BigDecimal totalInvested;
    private BigDecimal averagePurchasePrice;
    private long purchaseCount;
}
