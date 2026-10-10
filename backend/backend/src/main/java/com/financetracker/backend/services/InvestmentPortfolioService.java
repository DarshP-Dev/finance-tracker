package com.financetracker.backend.services;

import com.financetracker.backend.dto.PortfolioResponse;
import com.financetracker.backend.dto.PortfolioRefreshResponse;
import com.financetracker.backend.dto.InvestmentHoldingResponse;
import com.financetracker.backend.dto.PortfolioResponse.*;
import com.financetracker.backend.services.market.MarketDataProvider.Status;
import com.financetracker.backend.services.market.MarketQuoteService;
import com.financetracker.backend.services.market.MarketQuoteService.QuotePolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Stored-lot aggregation finishes its read transaction before any market HTTP begins. */
@Service @RequiredArgsConstructor
public class InvestmentPortfolioService {
    private final InvestmentService investments;
    private final MarketQuoteService quotes;
    public PortfolioResponse getPortfolio(Authentication authentication) {
        return getPortfolio(authentication, QuotePolicy.ON_DEMAND);
    }
    public PortfolioResponse getCachedPortfolio(Authentication authentication) {
        return getPortfolio(authentication, QuotePolicy.CACHE_ONLY);
    }
    public PortfolioResponse getPortfolio(Authentication authentication, QuotePolicy policy) {
        var stored = investments.getHoldings(authentication);
        var symbols = symbols(stored);
        return calculate(stored, policy == QuotePolicy.ON_DEMAND ? quotes.getQuotes(symbols) : quotes.getQuotes(symbols, policy));
    }
    public PortfolioRefreshResponse refreshPortfolio(Authentication authentication) {
        var stored = investments.getHoldings(authentication);
        var result = quotes.refreshQuotes(symbols(stored));
        return new PortfolioRefreshResponse(calculate(stored, result.quotes()), result.accepted()
                ? PortfolioRefreshResponse.Status.ACCEPTED : PortfolioRefreshResponse.Status.COOLDOWN, result.retryAfterSeconds());
    }
    private LinkedHashSet<String> symbols(List<InvestmentHoldingResponse> stored) {
        var symbols = new LinkedHashSet<String>();
        stored.forEach(h -> symbols.add(h.getTicker()));
        return symbols;
    }
    private PortfolioResponse calculate(List<InvestmentHoldingResponse> stored, Map<String, MarketQuoteService.QuoteResult> prices) {
        var holdings = new ArrayList<Holding>();
        BigDecimal totalCost = BigDecimal.ZERO, totalMarket = BigDecimal.ZERO;
        int quotedCount = 0;
        Instant lastUpdated = null;
        boolean stale = false;
        for (var h : stored) {
            var result = prices.get(h.getTicker());
            var quote = result == null ? null : result.quote();
            BigDecimal cost = money(h.getTotalInvested());
            BigDecimal market = quote == null ? null : money(h.getTotalShares().multiply(quote.currentPrice()));
            BigDecimal gain = market == null ? null : market.subtract(cost);
            totalCost = totalCost.add(cost);
            if (market != null) {
                quotedCount++; totalMarket = totalMarket.add(market);
                stale |= result.status() == Status.STALE;
                if (lastUpdated == null || result.fetchedAt().isBefore(lastUpdated)) lastUpdated = result.fetchedAt();
            }
            holdings.add(new Holding(h.getTicker(), h.getTotalShares(), h.getAveragePurchasePrice(), cost,
                    h.getPurchaseCount(), quote == null ? null : quote.currentPrice(), market, gain,
                    gain == null ? null : percent(gain, cost), null, "USD",
                    result == null ? Status.UNAVAILABLE : result.status(), result == null ? null : result.fetchedAt(),
                    quote == null ? null : quote.marketTimestamp(), quote == null ? null : quote.sessionDate(),
                    quote != null && quote.confirmedClose()));
        }
        boolean complete = quotedCount == stored.size();
        var status = stored.isEmpty() ? ValuationStatus.EMPTY : complete ? (stale ? ValuationStatus.STALE : ValuationStatus.AVAILABLE)
                : quotedCount > 0 ? ValuationStatus.PARTIAL
                : holdings.stream().allMatch(h -> h.quoteStatus() == Status.DISABLED) ? ValuationStatus.DISABLED : ValuationStatus.UNAVAILABLE;
        if (complete && totalMarket.signum() > 0) {
            BigDecimal denominator = totalMarket;
            holdings.replaceAll(h -> new Holding(h.ticker(), h.totalShares(), h.averagePurchasePrice(), h.totalInvested(), h.purchaseCount(),
                    h.currentPrice(), h.marketValue(), h.gainLoss(), h.returnPercentage(), percent(h.marketValue(), denominator),
                    h.currency(), h.quoteStatus(), h.lastUpdated(), h.marketTimestamp(), h.marketSessionDate(), h.confirmedClose()));
        }
        BigDecimal gain = complete ? totalMarket.subtract(totalCost) : null;
        return new PortfolioResponse(new Summary(totalCost, complete ? totalMarket : null, gain,
                gain == null ? null : percent(gain, totalCost), stored.size(), quotedCount, "USD", status, lastUpdated), holdings);
    }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal percent(BigDecimal value, BigDecimal denominator) {
        return denominator.signum() > 0 ? value.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP) : null;
    }
}
