package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.financetracker.backend.dto.InvestmentHoldingResponse;
import com.financetracker.backend.dto.PortfolioResponse.ValuationStatus;
import com.financetracker.backend.services.market.*;
import com.financetracker.backend.services.market.MarketDataProvider.Status;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class InvestmentPortfolioServiceTests {
    private final InvestmentService investments = mock(InvestmentService.class);
    private final MarketQuoteService quotes = mock(MarketQuoteService.class);
    private final InvestmentPortfolioService service = new InvestmentPortfolioService(investments, quotes);
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("owner", null);
    private final Instant now = Instant.parse("2026-10-07T12:00:00Z");
    @BeforeEach void setup() {
        when(investments.getHoldings(auth)).thenReturn(List.of(holding("AAPL", "10", "1825", "182.5")));
        when(quotes.getQuotes(Set.of("AAPL"))).thenReturn(Map.of("AAPL", quote("AAPL", "207.30", Status.AVAILABLE)));
    }
    @Test void exampleCostBasisMarketValueGainAndReturn() {
        var result = service.getPortfolio(auth);
        var h = result.holdings().getFirst();
        assertThat(h.totalInvested()).isEqualByComparingTo("1825");
        assertThat(h.currentPrice()).isEqualByComparingTo("207.30");
        assertThat(h.marketValue()).isEqualByComparingTo("2073");
        assertThat(h.gainLoss()).isEqualByComparingTo("248");
        assertThat(h.returnPercentage()).isEqualByComparingTo("13.59");
        assertThat(h.averagePurchasePrice()).isEqualByComparingTo("182.5");
        assertThat(h.allocationPercentage()).isEqualByComparingTo("100");
        assertThat(result.summary().totalCostBasis()).isEqualByComparingTo("1825");
        assertThat(result.summary().totalMarketValue()).isEqualByComparingTo("2073");
        assertThat(result.summary().totalGainLoss()).isEqualByComparingTo("248");
        assertThat(result.summary().totalReturnPercentage()).isEqualByComparingTo("13.59");
    }
    @Test void negativeUnrealizedReturn() {
        when(quotes.getQuotes(Set.of("AAPL"))).thenReturn(Map.of("AAPL", quote("AAPL", "170", Status.AVAILABLE)));
        var h = service.getPortfolio(auth).holdings().getFirst();
        assertThat(h.gainLoss()).isEqualByComparingTo("-125");
        assertThat(h.returnPercentage()).isEqualByComparingTo("-6.85");
    }
    @Test void zeroCostSkipsReturnSafely() {
        when(investments.getHoldings(auth)).thenReturn(List.of(holding("AAPL", "10", "0", "0")));
        assertThat(service.getPortfolio(auth).summary().totalReturnPercentage()).isNull();
        assertThat(service.getPortfolio(auth).holdings().getFirst().returnPercentage()).isNull();
    }
    @Test void multipleSymbolsTotalsAndMarketAllocation() {
        when(investments.getHoldings(auth)).thenReturn(List.of(holding("AAPL", "10", "1000", "100"), holding("MSFT", "5", "2000", "400")));
        when(quotes.getQuotes(Set.of("AAPL", "MSFT"))).thenReturn(Map.of("AAPL", quote("AAPL", "200", Status.AVAILABLE), "MSFT", quote("MSFT", "400", Status.AVAILABLE)));
        var response = service.getPortfolio(auth);
        assertThat(response.summary().totalMarketValue()).isEqualByComparingTo("4000");
        assertThat(response.summary().totalCostBasis()).isEqualByComparingTo("3000");
        assertThat(response.summary().totalGainLoss()).isEqualByComparingTo("1000");
        assertThat(response.summary().totalReturnPercentage()).isEqualByComparingTo("33.33");
        assertThat(response.holdings()).allSatisfy(h -> assertThat(h.allocationPercentage()).isEqualByComparingTo("50"));
        verify(quotes).getQuotes(Set.of("AAPL", "MSFT"));
    }
    @Test void partialPortfolioHasNoMisleadingTotalOrAllocation() {
        when(investments.getHoldings(auth)).thenReturn(List.of(holding("AAPL", "10", "1000", "100"), holding("MSFT", "5", "2000", "400")));
        when(quotes.getQuotes(Set.of("AAPL", "MSFT"))).thenReturn(Map.of("AAPL", quote("AAPL", "200", Status.AVAILABLE), "MSFT", new MarketQuoteService.QuoteResult(null, Status.UNKNOWN_SYMBOL, null)));
        var result = service.getPortfolio(auth);
        assertThat(result.summary().status()).isEqualTo(ValuationStatus.PARTIAL);
        assertThat(result.summary().totalMarketValue()).isNull();
        assertThat(result.summary().totalGainLoss()).isNull();
        assertThat(result.summary().quotedHoldingCount()).isEqualTo(1);
        assertThat(result.holdings()).allSatisfy(h -> assertThat(h.allocationPercentage()).isNull());
    }
    @Test void disabledAndMissingQuotesPreserveStoredCostAndShares() {
        when(quotes.getQuotes(Set.of("AAPL"))).thenReturn(Map.of("AAPL", new MarketQuoteService.QuoteResult(null, Status.DISABLED, null)));
        var result = service.getPortfolio(auth);
        assertThat(result.summary().status()).isEqualTo(ValuationStatus.DISABLED);
        assertThat(result.summary().totalMarketValue()).isNull();
        assertThat(result.holdings().getFirst().totalShares()).isEqualByComparingTo("10");
        assertThat(result.summary().totalCostBasis()).isEqualByComparingTo("1825");
    }
    @Test void staleValuationKeepsOriginalTimestamp() {
        when(quotes.getQuotes(Set.of("AAPL"))).thenReturn(Map.of("AAPL", quote("AAPL", "207.30", Status.STALE)));
        var result = service.getPortfolio(auth);
        assertThat(result.summary().status()).isEqualTo(ValuationStatus.STALE);
        assertThat(result.summary().lastUpdated()).isEqualTo(now);
    }
    @Test void emptyPortfolioHasZeroTotalsAndNoReturnOrAllocation() {
        when(investments.getHoldings(auth)).thenReturn(List.of());
        when(quotes.getQuotes(Set.of())).thenReturn(Map.of());
        assertThat(service.getPortfolio(auth).summary().status()).isEqualTo(ValuationStatus.EMPTY);
        assertThat(service.getPortfolio(auth).summary().totalReturnPercentage()).isNull();
    }
    @Test void fractionalSharesUseDecimalArithmeticAndOnlyRoundMoneyAtHoldingBoundary() {
        when(investments.getHoldings(auth)).thenReturn(List.of(holding("AAPL", "0.123456", "12.3456", "100")));
        var result = service.getPortfolio(auth);
        assertThat(result.holdings().getFirst().marketValue()).isEqualByComparingTo("25.59");
        assertThat(result.summary().totalGainLoss()).isEqualByComparingTo("13.24");
    }
    private InvestmentHoldingResponse holding(String ticker, String shares, String cost, String average) {
        return InvestmentHoldingResponse.builder().ticker(ticker).totalShares(new BigDecimal(shares)).totalInvested(new BigDecimal(cost)).averagePurchasePrice(new BigDecimal(average)).purchaseCount(2).build();
    }
    private MarketQuoteService.QuoteResult quote(String symbol, String price, Status status) {
        return new MarketQuoteService.QuoteResult(new MarketQuote(symbol, new BigDecimal(price), "USD", null, now), status, now);
    }
}
