package com.financetracker.backend.dto;

import com.financetracker.backend.services.market.MarketDataProvider.Status;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioResponse(Summary summary, List<Holding> holdings) {
    public enum ValuationStatus { AVAILABLE, STALE, PARTIAL, UNAVAILABLE, DISABLED, EMPTY }
    public record Summary(BigDecimal totalCostBasis, BigDecimal totalMarketValue, BigDecimal totalGainLoss,
                          BigDecimal totalReturnPercentage, int holdingCount, int quotedHoldingCount,
                          String currency, ValuationStatus status, Instant lastUpdated) {}
    public record Holding(String ticker, BigDecimal totalShares, BigDecimal averagePurchasePrice,
                          BigDecimal totalInvested, long purchaseCount, BigDecimal currentPrice,
                          BigDecimal marketValue, BigDecimal gainLoss, BigDecimal returnPercentage,
                          BigDecimal allocationPercentage, String currency, Status quoteStatus,
                          Instant lastUpdated, Instant marketTimestamp) {}
}
