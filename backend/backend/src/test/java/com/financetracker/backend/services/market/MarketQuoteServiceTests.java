package com.financetracker.backend.services.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static com.financetracker.backend.services.market.MarketDataProvider.Status.*;
import com.financetracker.backend.config.MarketDataProperties;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketQuoteServiceTests {
    private final MarketDataProvider provider = mock(MarketDataProvider.class);
    private final MarketDataProperties properties = new MarketDataProperties();
    private final MutableClock clock = new MutableClock();
    private MarketQuoteService service;
    @BeforeEach void setup() {
        properties.setEnabled(true);
        when(provider.isConfigured()).thenReturn(true);
        when(provider.getQuotes(anySet())).thenAnswer(call -> {
            Set<String> symbols = call.getArgument(0);
            Map<String, MarketDataProvider.Result> result = new LinkedHashMap<>();
            symbols.forEach(s -> result.put(s, new MarketDataProvider.Result(new MarketQuote(s, new BigDecimal("100"), "USD", null, clock.instant()), AVAILABLE)));
            return result;
        });
        service = new MarketQuoteService(provider, properties, clock);
    }
    @Test void normalizedUniqueSymbolsShareSingleBatch() {
        var result = service.getQuotes(Set.of(" aapl ", "AAPL", "msft"));
        assertThat(result.keySet()).containsExactly("AAPL", "MSFT");
        verify(provider).getQuotes(Set.of("AAPL", "MSFT"));
    }
    @Test void cachedQuotesAreSharedAcrossRequests() {
        service.getQuotes(Set.of("AAPL"));
        clock.advance(599);
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void expiredCacheRefreshes() {
        service.getQuotes(Set.of("AAPL"));
        clock.advance(600);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void disabledMarketDataDoesNotContactProvider() {
        properties.setEnabled(false);
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(DISABLED);
        verifyNoInteractions(provider);
    }
    @Test void missingKeyDoesNotRequestQuotes() {
        when(provider.isConfigured()).thenReturn(false);
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(UNAVAILABLE);
        verify(provider, never()).getQuotes(anySet());
    }
    @Test void failureIsCachedWithoutFakeZeroPrice() {
        when(provider.getQuotes(anySet())).thenThrow(new IllegalStateException());
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(UNAVAILABLE);
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void staleOnRefreshFailureKeepsOriginalFetchedAt() {
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        clock.advance(600);
        when(provider.getQuotes(anySet())).thenThrow(new IllegalStateException());
        var result = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(result.status()).isEqualTo(STALE);
        assertThat(result.fetchedAt()).isEqualTo(first.fetchedAt());
        assertThat(result.quote()).isEqualTo(first.quote());
    }
    @Test void tooOldStalePriceIsNotReturned() {
        service.getQuotes(Set.of("AAPL"));
        clock.advance(86400);
        when(provider.getQuotes(anySet())).thenThrow(new IllegalStateException());
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
    }
    @Test void invalidTickerDoesNotBecomeRequestFragment() {
        var result = service.getQuotes(Set.of("AAPL&apikey=secret"));
        assertThat(result.values()).allSatisfy(q -> assertThat(q.status()).isEqualTo(INVALID_SYMBOL));
        verify(provider, never()).getQuotes(anySet());
    }
    @Test void minuteCreditGuardPreventsRepeatedProviderCallsForNewSymbols() {
        properties.setCreditsPerMinute(1);
        assertThat(service.getQuotes(Set.of("AAPL", "MSFT")).get("MSFT").status()).isEqualTo(RATE_LIMITED);
        clock.advance(60);
        assertThat(service.getQuotes(Set.of("MSFT")).get("MSFT").status()).isEqualTo(AVAILABLE);
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void dailyCreditGuardDoesNotResetEachMinute() {
        properties.setDailyReserve(0);
        properties.setCreditsPerDay(1);
        service.getQuotes(Set.of("AAPL"));
        clock.advance(60);
        assertThat(service.getQuotes(Set.of("MSFT")).get("MSFT").status()).isEqualTo(RATE_LIMITED);
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void cacheIsBounded() {
        properties.setMaxCacheSymbols(1);
        service.getQuotes(Set.of("AAPL"));
        service.getQuotes(Set.of("MSFT"));
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(3)).getQuotes(anySet());
    }
    @Test void concurrentMissesShareOneProviderCall() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.getQuotes(Set.of("AAPL")));
            var second = executor.submit(() -> service.getQuotes(Set.of("AAPL")));
            assertThat(first.get()).isEqualTo(second.get());
        }
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void partialCacheOnlyFetchesTheMissingSymbol() {
        service.getQuotes(Set.of("AAPL", "MSFT", "VOO"));
        service.getQuotes(Set.of("AAPL", "MSFT", "VOO", "NVDA"));
        verify(provider).getQuotes(Set.of("AAPL", "MSFT", "VOO"));
        verify(provider).getQuotes(Set.of("NVDA"));
        assertThat(service.usage().symbolsRequested()).isEqualTo(4);
    }
    @Test void cachedOnlyNeverFetchesColdOrExpiredSymbols() {
        assertThat(service.getQuotes(Set.of("AAPL"), MarketQuoteService.QuotePolicy.CACHE_ONLY).get("AAPL").quote()).isNull();
        verify(provider, never()).getQuotes(anySet());
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        clock.advance(601);
        var cached = service.getQuotes(Set.of("AAPL", "MSFT"), MarketQuoteService.QuotePolicy.CACHE_ONLY);
        assertThat(cached.get("AAPL").status()).isEqualTo(STALE);
        assertThat(cached.get("AAPL").fetchedAt()).isEqualTo(first.fetchedAt());
        assertThat(cached.get("MSFT").quote()).isNull();
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void addingTickerCanKeepExpiredButUsableQuotes() {
        service.getQuotes(Set.of("AAPL", "MSFT"));
        clock.advance(601);
        var result = service.getQuotes(Set.of("AAPL", "MSFT", "NVDA"), MarketQuoteService.QuotePolicy.MISSING_ONLY);
        assertThat(result.get("AAPL").status()).isEqualTo(STALE);
        verify(provider).getQuotes(Set.of("NVDA"));
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void manualRefreshIsBackendProtectedAndDoesNotBypassCache() {
        var first = service.refreshQuotes(Set.of("AAPL"));
        assertThat(first.accepted()).isTrue();
        for (int attempt = 0; attempt < 5; attempt++) {
            var blocked = service.refreshQuotes(Set.of("AAPL", "MSFT"));
            assertThat(blocked.accepted()).isFalse();
            assertThat(blocked.retryAfterSeconds()).isEqualTo(120);
            assertThat(blocked.quotes().get("AAPL").quote()).isNotNull();
            assertThat(blocked.quotes().get("MSFT").quote()).isNull();
        }
        clock.advance(120);
        assertThat(service.refreshQuotes(Set.of("AAPL")).accepted()).isTrue();
        verify(provider, times(1)).getQuotes(anySet());
        assertThat(service.usage().cooldownBlocks()).isEqualTo(5);
    }
    @Test void normalGetCannotBypassMinimumQuoteRefreshSpacing() {
        properties.setCacheDuration(Duration.ofSeconds(60));
        service.refreshQuotes(Set.of("AAPL"));
        clock.advance(60);
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(STALE);
        verify(provider, times(1)).getQuotes(anySet());
        clock.advance(60);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void concurrentManualRefreshesCannotMultiplyCalls() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.refreshQuotes(Set.of("AAPL")));
            var second = executor.submit(() -> service.refreshQuotes(Set.of("AAPL")));
            assertThat(List.of(first.get().accepted(), second.get().accepted())).containsExactlyInAnyOrder(true, false);
        }
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void emptyPortfolioAndRefreshNeverContactProviderOrStartCooldown() {
        assertThat(service.getQuotes(Set.of())).isEmpty();
        assertThat(service.refreshQuotes(Set.of()).retryAfterSeconds()).isZero();
        verifyNoInteractions(provider);
        assertThat(service.refreshQuotes(Set.of("AAPL")).accepted()).isTrue();
    }
    @Test void unknownSymbolIsNegativelyCachedForThirtyMinutesThenRetried() {
        when(provider.getQuotes(anySet())).thenReturn(Map.of("AAPL", MarketDataProvider.Result.unavailable(UNKNOWN_SYMBOL)));
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
        clock.advance(1799);
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(UNKNOWN_SYMBOL);
        verify(provider, times(1)).getQuotes(anySet());
        clock.advance(1);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void unsupportedAssetAndCurrencyResultsDoNotRepeatAcrossPages() {
        when(provider.getQuotes(anySet())).thenReturn(Map.of("AAPL", MarketDataProvider.Result.unavailable(UNSUPPORTED_ASSET),
                "MSFT", MarketDataProvider.Result.unavailable(UNSUPPORTED_CURRENCY)));
        service.getQuotes(Set.of("AAPL", "MSFT"));
        clock.advance(900);
        var result = service.getQuotes(Set.of("AAPL", "MSFT"));
        assertThat(result.get("AAPL").status()).isEqualTo(UNSUPPORTED_ASSET);
        assertThat(result.get("MSFT").quote()).isNull();
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void unavailablePriceWithoutFallbackHasLongNegativeCache() {
        when(provider.getQuotes(anySet())).thenReturn(Map.of("AAPL", MarketDataProvider.Result.unavailable(UNAVAILABLE)));
        service.getQuotes(Set.of("AAPL"));
        clock.advance(900);
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void dailyReserveStopsBeforeEntireCreditBudgetIsSpent() {
        properties.setCreditsPerDay(3); properties.setDailyReserve(1);
        service.getQuotes(Set.of("AAPL", "MSFT", "VOO"));
        clock.advance(60);
        assertThat(service.getQuotes(Set.of("VOO")).get("VOO").status()).isEqualTo(RATE_LIMITED);
        verify(provider).getQuotes(Set.of("AAPL", "MSFT"));
        verify(provider, times(1)).getQuotes(anySet());
        assertThat(service.usage().dayCredits()).isEqualTo(2);
    }
    @Test void reserveCannotBeBypassedBySmallBudgetOrInvalidConfiguration() {
        properties.setCreditsPerDay(1); properties.setDailyReserve(50);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, never()).getQuotes(anySet());
    }
    @Test void requestCountGuardIsSeparateFromSymbolCredits() {
        properties.setMaxRequestsPerMinute(1);
        service.getQuotes(Set.of("AAPL", "MSFT"));
        assertThat(service.getQuotes(Set.of("VOO")).get("VOO").status()).isEqualTo(RATE_LIMITED);
        assertThat(service.usage().minuteCredits()).isEqualTo(2);
        assertThat(service.usage().minuteRequests()).isEqualTo(1);
        clock.advance(60);
        service.getQuotes(Set.of("VOO"));
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void dailyRequestCountDoesNotResetEachMinute() {
        properties.setMaxRequestsPerDay(1);
        service.getQuotes(Set.of("AAPL"));
        clock.advance(60);
        service.getQuotes(Set.of("MSFT"));
        verify(provider, times(1)).getQuotes(anySet());
        assertThat(service.usage().quotaBlocks()).isEqualTo(1);
    }
    @Test void providerRateLimitBacksOffAcrossDifferentSymbolsWithoutRetry() {
        when(provider.getQuotes(anySet())).thenReturn(Map.of("AAPL", MarketDataProvider.Result.unavailable(RATE_LIMITED)));
        service.getQuotes(Set.of("AAPL"));
        clock.advance(60);
        service.getQuotes(Set.of("MSFT"));
        clock.advance(60);
        service.getQuotes(Set.of("NVDA"));
        verify(provider, times(1)).getQuotes(anySet());
        assertThat(service.usage().backoffBlocks()).isEqualTo(2);
        clock.advance(180);
        service.getQuotes(Set.of("VOO"));
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void exhaustedQuotaRetainsStaleQuoteAndOriginalTimestamp() {
        properties.setCreditsPerDay(1); properties.setDailyReserve(0);
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        clock.advance(601);
        var result = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(result.quote()).isEqualTo(first.quote());
        assertThat(result.status()).isEqualTo(STALE);
        assertThat(result.fetchedAt()).isEqualTo(first.fetchedAt());
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void unchangedMarketTimestampAndPriceDelayLowValueRefreshes() {
        var quote = new MarketQuote("AAPL", new BigDecimal("100"), "USD", null, clock.instant());
        when(provider.getQuotes(anySet())).thenReturn(Map.of("AAPL", new MarketDataProvider.Result(quote, AVAILABLE)));
        service.getQuotes(Set.of("AAPL"));
        clock.advance(600);
        service.getQuotes(Set.of("AAPL"));
        clock.advance(600);
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(STALE);
        verify(provider, times(2)).getQuotes(anySet());
        clock.advance(1200);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(3)).getQuotes(anySet());
    }
    @Test void usageCountersTrackBatchesAndCacheSavingsWithoutPrivateData() {
        service.getQuotes(Set.of("AAPL", "MSFT"));
        service.getQuotes(Set.of("AAPL", "MSFT"));
        clock.advance(601);
        service.getQuotes(Set.of("AAPL", "MSFT"), MarketQuoteService.QuotePolicy.CACHE_ONLY);
        var usage = service.usage();
        assertThat(usage.providerRequests()).isEqualTo(1);
        assertThat(usage.symbolsRequested()).isEqualTo(2);
        assertThat(usage.cacheHits()).isEqualTo(4);
        assertThat(usage.cacheMisses()).isEqualTo(2);
        assertThat(usage.staleCacheHits()).isEqualTo(2);
        assertThat(usage.lastBatchSize()).isEqualTo(2);
        assertThat(usage.toString()).doesNotContain("AAPL", "MSFT", "apiKey");
    }
    @Test void closedMarketNeverFetchesColdPricesIncludingManualRefreshAndNewTickers() {
        clock.now = Instant.parse("2026-10-07T21:00:00Z");
        for (var policy : MarketQuoteService.QuotePolicy.values())
            assertThat(service.getQuotes(Set.of("AAPL"), policy).get("AAPL").quote()).isNull();
        assertThat(service.refreshQuotes(Set.of("MSFT")).quotes().get("MSFT").quote()).isNull();
        assertThat(service.usage().providerRequests()).isZero();
        assertThat(service.usage().symbolsRequested()).isZero();
        verify(provider, never()).getQuotes(anySet());
    }
    @Test void weekendKeepsLastSessionSnapshotWithoutChangingTimestampsOrConsumingCredits() {
        clock.now = Instant.parse("2026-10-09T19:00:00Z");
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        clock.now = Instant.parse("2026-10-11T20:00:00Z");
        var weekend = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(weekend.quote()).isEqualTo(first.quote());
        assertThat(weekend.fetchedAt()).isEqualTo(first.fetchedAt());
        assertThat(weekend.status()).isEqualTo(STALE);
        service.refreshQuotes(Set.of("AAPL"));
        verify(provider, times(1)).getQuotes(anySet());
        assertThat(service.usage().symbolsRequested()).isEqualTo(1);
        clock.now = Instant.parse("2026-10-12T13:30:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void afterCloseRepeatedReadsReuseOnePriceAndNextOpenRefreshes() {
        clock.now = Instant.parse("2026-10-07T19:59:00Z");
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        clock.now = Instant.parse("2026-10-07T23:00:00Z");
        for (int i = 0; i < 5; i++)
            assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isEqualTo(first.quote());
        verify(provider, times(1)).getQuotes(anySet());
        clock.now = Instant.parse("2026-10-08T13:30:00Z");
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void scheduledHolidayDoesNotFetchAndOldUnrelatedSessionQuoteIsNotKeptForever() {
        clock.now = Instant.parse("2026-11-23T16:00:00Z");
        service.getQuotes(Set.of("AAPL"));
        clock.now = Instant.parse("2026-11-26T16:00:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void holidayWeekendKeepsTheMostRecentTradingSessionSnapshot() {
        clock.now = Instant.parse("2026-07-02T19:00:00Z");
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        clock.now = Instant.parse("2026-07-05T16:00:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").fetchedAt()).isEqualTo(first.fetchedAt());
        verify(provider, times(1)).getQuotes(anySet());
    }
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T16:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
