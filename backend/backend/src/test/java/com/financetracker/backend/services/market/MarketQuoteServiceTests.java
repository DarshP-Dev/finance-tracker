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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class MarketQuoteServiceTests {
    private final MarketDataProvider provider = mock(MarketDataProvider.class);
    private final MarketQuoteSnapshotStore snapshots = mock(MarketQuoteSnapshotStore.class);
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
        when(provider.getClosingQuotes(anySet(), any())).thenAnswer(call -> {
            Set<String> symbols = call.getArgument(0);
            LocalDate session = call.getArgument(1);
            Map<String, MarketDataProvider.Result> result = new LinkedHashMap<>();
            symbols.forEach(s -> result.put(s, new MarketDataProvider.Result(
                    new MarketQuote(s, new BigDecimal("100"), "USD", null, null, session, true), AVAILABLE)));
            return result;
        });
        service = new MarketQuoteService(provider, properties, clock, snapshots);
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
    @Test void identicalCurrentSessionBarsStillRefreshAfterTenMinuteCacheExpires() {
        var quote = new MarketQuote("AAPL", new BigDecimal("100"), "USD", null, clock.instant());
        when(provider.getQuotes(anySet())).thenReturn(Map.of("AAPL", new MarketDataProvider.Result(quote, AVAILABLE)));
        service.getQuotes(Set.of("AAPL"));
        clock.advance(600);
        service.getQuotes(Set.of("AAPL"));
        clock.advance(600);
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        verify(provider, times(3)).getQuotes(anySet());
        clock.advance(1200);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(4)).getQuotes(anySet());
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
    @Test void closedMarketFetchesColdPricesOnceExceptForCacheOnly() {
        clock.now = Instant.parse("2026-10-07T21:00:00Z");
        assertThat(service.getQuotes(Set.of("AAPL"), MarketQuoteService.QuotePolicy.CACHE_ONLY).get("AAPL").quote()).isNull();
        for (var policy : MarketQuoteService.QuotePolicy.values())
            assertThat(service.getQuotes(Set.of("AAPL"), policy).get("AAPL").status()).isEqualTo(STALE);
        assertThat(service.refreshQuotes(Set.of("AAPL")).quotes().get("AAPL").quote()).isNotNull();
        verify(provider, times(1)).getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 7));
        verify(snapshots).save(anyMap(), eq(clock.instant()));
    }
    @Test void weekendKeepsLastSessionSnapshotWithoutChangingTimestampsOrConsumingCredits() {
        clock.now = Instant.parse("2026-10-09T21:00:00Z");
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        clock.now = Instant.parse("2026-10-11T20:00:00Z");
        var weekend = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(weekend.quote()).isEqualTo(first.quote());
        assertThat(weekend.fetchedAt()).isEqualTo(first.fetchedAt());
        assertThat(weekend.status()).isEqualTo(STALE);
        service.refreshQuotes(Set.of("AAPL"));
        verify(provider, times(1)).getClosingQuotes(anySet(), any());
        assertThat(service.usage().symbolsRequested()).isEqualTo(1);
        clock.now = Instant.parse("2026-10-12T13:30:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void afterCloseRepeatedReadsReuseOnePriceAndNextOpenRefreshes() {
        clock.now = Instant.parse("2026-10-07T19:59:00Z");
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        clock.now = Instant.parse("2026-10-07T23:00:00Z");
        var closing = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(closing.quote().currentPrice()).isEqualByComparingTo(first.quote().currentPrice());
        assertThat(closing.quote().confirmedClose()).isTrue();
        for (int i = 0; i < 5; i++)
            assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isEqualTo(closing.quote());
        verify(provider, times(1)).getClosingQuotes(anySet(), any());
        verify(provider, times(1)).getQuotes(anySet());
        clock.now = Instant.parse("2026-10-08T13:30:00Z");
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void scheduledHolidayFetchesLatestCompletedSessionWhenStoredIntradayQuoteIsOlder() {
        clock.now = Instant.parse("2026-11-23T16:00:00Z");
        service.getQuotes(Set.of("AAPL"));
        clock.now = Instant.parse("2026-11-26T16:00:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(STALE);
        verify(provider, times(1)).getQuotes(anySet());
        verify(provider).getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 11, 25));
    }
    @Test void holidayWeekendKeepsTheMostRecentTradingSessionSnapshot() {
        clock.now = Instant.parse("2026-07-02T21:00:00Z");
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        clock.now = Instant.parse("2026-07-05T16:00:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").fetchedAt()).isEqualTo(first.fetchedAt());
        verify(provider, times(1)).getClosingQuotes(anySet(), any());
    }
    @Test void everySuccessfulBatchIsPersistedWithActualTimestamps() {
        service.getQuotes(Set.of("AAPL", "MSFT"));
        verify(snapshots).save(argThat(saved -> saved.size() == 2
                && saved.get("AAPL").fetchedAt().equals(clock.instant())
                && saved.get("MSFT").quote().marketTimestamp().equals(clock.instant())), eq(clock.instant()));
        clock.advance(600);
        service.getQuotes(Set.of("AAPL", "MSFT"));
        verify(snapshots, times(2)).save(anyMap(), any());
    }
    @Test void freshMemorySkipsBothSnapshotLookupAndProvider() {
        service.getQuotes(Set.of("AAPL"));
        clearInvocations(snapshots, provider);
        service.getQuotes(Set.of("AAPL"));
        verifyNoInteractions(snapshots, provider);
    }
    @Test void closedRestartLoadsBatchSnapshotsAndWarmsMemoryWithoutChangingTimes() {
        var fetched = clock.instant();
        clock.now = Instant.parse("2026-10-07T22:00:00Z");
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", closingSaved("AAPL", fetched), "MSFT", closingSaved("MSFT", fetched)));
        var result = service.getQuotes(Set.of(" aapl ", "AAPL", "MSFT"));
        assertThat(result.get("AAPL").status()).isEqualTo(STALE);
        assertThat(result.get("AAPL").fetchedAt()).isEqualTo(fetched);
        service.getQuotes(Set.of("AAPL", "MSFT"));
        service.refreshQuotes(Set.of("AAPL", "MSFT"));
        verify(snapshots, times(1)).load(Set.of("AAPL", "MSFT"));
        verify(snapshots, never()).save(anyMap(), any());
        verifyNoInteractions(provider);
    }
    @Test void weekendHolidayAndEarlyCloseRestartsLoadLastKnownSnapshots() {
        for (String date : List.of("2026-10-11T16:00:00Z", "2026-11-26T16:00:00Z", "2026-11-27T19:00:00Z")) {
            clock.now = Instant.parse(date);
            when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", closingSaved("AAPL", clock.instant().minusSeconds(1800))));
            var restarted = new MarketQuoteService(provider, properties, clock, snapshots);
            assertThat(restarted.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(STALE);
        }
        verify(provider, never()).getQuotes(anySet());
    }
    @Test void closedPartialMemoryAndDatabaseFetchOnlyUniqueUnresolvedSymbols() {
        clock.now = Instant.parse("2026-10-07T22:00:00Z");
        service.getQuotes(Set.of("AAPL"));
        when(snapshots.load(anySet())).thenReturn(Map.of("MSFT", closingSaved("MSFT", clock.instant().minusSeconds(3600)),
                "VOO", closingSaved("VOO", clock.instant().minusSeconds(3600))));
        service.getQuotes(Set.of("AAPL", "MSFT", "VOO", "NVDA", " nvda "));
        verify(snapshots).load(Set.of("MSFT", "VOO", "NVDA"));
        verify(provider).getClosingQuotes(eq(Set.of("NVDA")), any());
        verify(provider, times(2)).getClosingQuotes(anySet(), any());
    }
    @Test void openMarketRefreshesEvenRecentPersistedSnapshots() {
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", clock.instant().minusSeconds(60))));
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        verify(provider).getQuotes(Set.of("AAPL"));
    }
    @Test void nextSessionRefreshesWarmedPersistentQuote() {
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", Instant.parse("2026-10-09T19:57:00Z"))));
        service.getQuotes(Set.of("AAPL"));
        clock.now = Instant.parse("2026-10-12T13:35:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        verify(provider).getQuotes(Set.of("AAPL"));
    }
    @Test void preOpenProviderQuoteDoesNotSuppressNextSessionRefreshInsideTtl() {
        clock.now = Instant.parse("2026-10-08T13:29:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(STALE);
        clock.advance(60);
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        verify(provider, times(1)).getQuotes(anySet());
        verify(provider, times(1)).getClosingQuotes(anySet(), any());
    }
    @Test void cacheOnlyNeverLabelsPreOpenQuoteAsFreshInNewSession() {
        clock.now = Instant.parse("2026-10-08T13:29:00Z");
        service.getQuotes(Set.of("AAPL"));
        clock.advance(60);
        assertThat(service.getQuotes(Set.of("AAPL"), MarketQuoteService.QuotePolicy.CACHE_ONLY).get("AAPL").status()).isEqualTo(STALE);
        verify(provider, times(1)).getClosingQuotes(anySet(), any());
    }
    @Test void closedColdSymbolsStillObeyQuotaAndProviderWideBackoff() {
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        properties.setCreditsPerDay(1); properties.setDailyReserve(0);
        service.getQuotes(Set.of("AAPL", "MSFT"));
        assertThat(service.getQuotes(Set.of("MSFT")).get("MSFT").status()).isEqualTo(RATE_LIMITED);
        verify(provider, times(1)).getClosingQuotes(eq(Set.of("AAPL")), any());
        service = new MarketQuoteService(provider, properties, clock, snapshots);
        properties.setCreditsPerDay(800);
        when(provider.getClosingQuotes(anySet(), any())).thenThrow(new IllegalStateException());
        service.getQuotes(Set.of("NVDA"));
        service.getQuotes(Set.of("VOO"));
        verify(provider, never()).getClosingQuotes(eq(Set.of("VOO")), any());
        assertThat(service.usage().backoffBlocks()).isEqualTo(1);
    }
    @Test void providerFailureUsesPersistedFallbackOlderThanMemoryStaleAge() {
        var fetched = clock.instant().minusSeconds(3 * 86400);
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", fetched)));
        when(provider.getQuotes(anySet())).thenThrow(new IllegalStateException());
        var result = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(result.status()).isEqualTo(STALE);
        assertThat(result.quote().currentPrice()).isEqualByComparingTo("210.25");
        assertThat(result.fetchedAt()).isEqualTo(fetched);
    }
    @Test void persistedFallbackHonorsQuotaReserveWithoutFetching() {
        properties.setCreditsPerDay(1); properties.setDailyReserve(1);
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", clock.instant().minusSeconds(86400))));
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(STALE);
        verify(provider, never()).getQuotes(anySet());
    }
    @Test void tooOldClosedSnapshotAllowsOneFetchAndPersistsIt() {
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", clock.instant().minusSeconds(7 * 86400))));
        service.getQuotes(Set.of("AAPL"));
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(1)).getClosingQuotes(eq(Set.of("AAPL")), any());
        verify(snapshots).save(anyMap(), eq(clock.instant()));
    }
    @Test void configurablePersistentMaxAgeRejectsTooOldFallbackAndNeverReturnsZero() {
        properties.setPersistedMaxAgeDays(2);
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", clock.instant().minusSeconds(3 * 86400))));
        when(provider.getClosingQuotes(anySet(), any())).thenThrow(new IllegalStateException());
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
        service.refreshQuotes(Set.of("AAPL"));
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(1)).getClosingQuotes(anySet(), any());
    }
    @Test void cacheOnlyLoadsPersistentSnapshotsWithoutExternalCallsEvenDuringOpenSession() {
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", clock.instant().minusSeconds(60))));
        assertThat(service.getQuotes(Set.of("AAPL", "MSFT"), MarketQuoteService.QuotePolicy.CACHE_ONLY).get("AAPL").status()).isEqualTo(STALE);
        assertThat(service.getQuotes(Set.of("MSFT"), MarketQuoteService.QuotePolicy.CACHE_ONLY).get("MSFT").quote()).isNull();
        verifyNoInteractions(provider);
    }
    @Test void missingProviderCredentialsStillAllowPersistentFallback() {
        when(provider.isConfigured()).thenReturn(false);
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", clock.instant().minusSeconds(60))));
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(STALE);
        verify(provider, never()).getQuotes(anySet());
    }
    @Test void snapshotWriteFailureLeavesSuccessfulMemoryResponseUsable() {
        doThrow(new IllegalStateException("private details")).when(snapshots).save(anyMap(), any());
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void snapshotReadFailureDoesNotPreventGuardedProviderFetch() {
        when(snapshots.load(anySet())).thenThrow(new IllegalStateException("private details"));
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        verify(provider).getQuotes(Set.of("AAPL"));
    }
    @Test void invalidProviderQuotesAreNeverPersistedOrRepresentedAsZero() {
        when(provider.getQuotes(anySet())).thenReturn(Map.of("AAPL", new MarketDataProvider.Result(
                new MarketQuote("AAPL", BigDecimal.ZERO, "USD", null, clock.instant()), AVAILABLE)));
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
        verify(snapshots, never()).save(anyMap(), any());
    }
    @Test void invalidFutureDatedOrNonUsdSnapshotsAreRejected() {
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", clock.instant().plusSeconds(60)),
                "MSFT", new MarketQuoteSnapshotStore.Snapshot(new MarketQuote("MSFT", BigDecimal.ZERO, "USD", null, null), clock.instant()),
                "VOO", new MarketQuoteSnapshotStore.Snapshot(new MarketQuote("VOO", BigDecimal.ONE, "EUR", null, null), clock.instant())));
        assertThat(service.getQuotes(Set.of("AAPL", "MSFT", "VOO"), MarketQuoteService.QuotePolicy.CACHE_ONLY).values())
                .allSatisfy(r -> assertThat(r.quote()).isNull());
        verifyNoInteractions(provider);
    }
    @Test void closedMemoryQuotesAlsoExpireAtPersistentAgeLimit() {
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        service.getQuotes(Set.of("AAPL"));
        clock.advance(7 * 86400);
        when(provider.getClosingQuotes(anySet(), any())).thenThrow(new IllegalStateException());
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
        verify(provider, times(2)).getClosingQuotes(anySet(), any());
    }
    @Test void recentRetrievalOfPreviousSessionDoesNotSuppressLatestEodRequest() {
        clock.now = Instant.parse("2026-10-09T23:12:00Z");
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", Instant.parse("2026-10-09T04:13:00Z"))));
        var result = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(result.quote().sessionDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(result.quote().confirmedClose()).isTrue();
        verify(provider).getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9));
        verify(provider, never()).getQuotes(anySet());
    }
    @Test void regularAndEarlyClosesWaitForPublicationThenFetchOnlyOnce() {
        for (String close : List.of("2026-10-09T20:00:00Z", "2026-11-27T18:00:00Z")) {
            var resolver = new MarketQuoteService(provider, properties, clock, snapshots);
            clock.now = Instant.parse(close);
            clearInvocations(provider);
            resolver.getQuotes(Set.of("AAPL"));
            clock.advance(899);
            resolver.getQuotes(Set.of("AAPL"));
            verifyNoInteractions(provider);
            clock.advance(1);
            assertThat(resolver.getQuotes(Set.of("AAPL")).get("AAPL").quote().confirmedClose()).isTrue();
            resolver.refreshQuotes(Set.of("AAPL"));
            resolver.getQuotes(Set.of("AAPL"));
            verify(provider, times(1)).getClosingQuotes(eq(Set.of("AAPL")), any());
        }
    }
    @Test void unexpiredIntradayTtlCannotSuppressClosingRequestAfterPublicationDelay() {
        properties.setCacheDuration(Duration.ofHours(1));
        clock.now = Instant.parse("2026-10-09T19:59:00Z");
        service.getQuotes(Set.of("AAPL"));
        clock.now = Instant.parse("2026-10-09T20:15:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").quote().confirmedClose()).isTrue();
        verify(provider).getQuotes(Set.of("AAPL"));
        verify(provider).getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9));
    }
    @Test void latestConfirmedSessionRemainsUsableBeyondOrdinaryFallbackAge() {
        properties.setPersistedMaxAgeDays(1);
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        var original = Instant.parse("2026-10-09T21:00:00Z");
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", closingSaved("AAPL", original)));
        var result = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(result.quote().confirmedClose()).isTrue();
        assertThat(result.fetchedAt()).isEqualTo(original);
        service.getQuotes(Set.of("AAPL"));
        verifyNoInteractions(provider);
    }
    @Test void staleClosingRetryCooldownCannotBlockNextOpenSession() {
        clock.now = Instant.parse("2026-10-12T13:29:00Z");
        when(provider.getClosingQuotes(anySet(), any())).thenReturn(Map.of("AAPL", new MarketDataProvider.Result(
                new MarketQuote("AAPL", new BigDecimal("90"), "USD", null, null, LocalDate.of(2026, 10, 8), true), STALE)));
        service.getQuotes(Set.of("AAPL"));
        clock.now = Instant.parse("2026-10-12T13:30:00Z");
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(AVAILABLE);
        verify(provider).getQuotes(Set.of("AAPL"));
    }
    @Test void outdatedEodResponseIsStaleAndUsesCooldownUntilLatestSessionArrives() {
        clock.now = Instant.parse("2026-10-09T22:00:00Z");
        var older = new MarketQuote("AAPL", new BigDecimal("90"), "USD", null, null, LocalDate.of(2026, 10, 8), true);
        when(provider.getClosingQuotes(anySet(), any())).thenReturn(Map.of("AAPL", new MarketDataProvider.Result(older, STALE)));
        var first = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(first.status()).isEqualTo(STALE);
        assertThat(first.quote().sessionDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        clock.advance(1799);
        service.getQuotes(Set.of("AAPL")); service.refreshQuotes(Set.of("AAPL"));
        verify(provider, times(1)).getClosingQuotes(anySet(), any());
        clock.advance(1);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(2)).getClosingQuotes(anySet(), any());
    }
    @Test void olderProviderDataCannotReplaceNewerFallbackOrItsRetrievalTime() {
        service.getQuotes(Set.of("AAPL"));
        Instant original = clock.instant();
        clock.advance(600);
        when(provider.getQuotes(anySet())).thenReturn(Map.of("AAPL", new MarketDataProvider.Result(
                new MarketQuote("AAPL", new BigDecimal("90"), "USD", null, original.minusSeconds(86400)), AVAILABLE)));
        var result = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(result.status()).isEqualTo(STALE);
        assertThat(result.quote().currentPrice()).isEqualByComparingTo("100");
        assertThat(result.fetchedAt()).isEqualTo(original);
        clock.advance(600);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(2)).getQuotes(anySet());
        verify(snapshots, times(1)).save(anyMap(), any());
    }
    @Test void priorSessionTimestampNeverBecomesFreshFromRecentRetrieval() {
        when(provider.getQuotes(anySet())).thenReturn(Map.of("AAPL", new MarketDataProvider.Result(
                new MarketQuote("AAPL", new BigDecimal("90"), "USD", null, clock.instant().minusSeconds(86400)), AVAILABLE)));
        assertThat(service.getQuotes(Set.of("AAPL")).get("AAPL").status()).isEqualTo(STALE);
        clock.advance(600);
        service.getQuotes(Set.of("AAPL"));
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void disabledProviderStillReturnsStoredFallbackAndNeverMakesHttpRequests() {
        properties.setEnabled(false);
        var original = clock.instant().minusSeconds(86400);
        when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", original)));
        var result = service.getQuotes(Set.of("AAPL")).get("AAPL");
        assertThat(result.status()).isEqualTo(STALE);
        assertThat(result.fetchedAt()).isEqualTo(original);
        assertThat(result.quote().currentPrice()).isEqualByComparingTo("210.25");
        verifyNoInteractions(provider);
    }
    @Test void concurrentClosedRequestsShareOneEodBatchAndCreditBudget() throws Exception {
        clock.now = Instant.parse("2026-10-09T22:00:00Z");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = new ArrayList<java.util.concurrent.Future<Map<String, MarketQuoteService.QuoteResult>>>();
            for (int i = 0; i < 10; i++) requests.add(executor.submit(() -> service.getQuotes(Set.of("AAPL", "MSFT"))));
            for (var request : requests) assertThat(request.get().values()).allSatisfy(r -> assertThat(r.quote().confirmedClose()).isTrue());
        }
        verify(provider, times(1)).getClosingQuotes(eq(Set.of("AAPL", "MSFT")), any());
        assertThat(service.usage().minuteCredits()).isEqualTo(2);
    }
    @Test void closingPricesAreRefetchedForNextCompletedSessionButNotDuringWeekend() {
        clock.now = Instant.parse("2026-10-08T22:00:00Z");
        service.getQuotes(Set.of("AAPL"));
        clock.now = Instant.parse("2026-10-09T22:00:00Z");
        service.getQuotes(Set.of("AAPL"));
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        service.getQuotes(Set.of("AAPL"));
        verify(provider).getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 8));
        verify(provider).getClosingQuotes(Set.of("AAPL"), LocalDate.of(2026, 10, 9));
        verify(provider, times(2)).getClosingQuotes(anySet(), any());
    }
    @Test void missingProviderConfigurationLogsDecisionAndReturnsFallbackWithoutAttemptingEod(CapturedOutput output) {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MarketQuoteService.class);
        var previous = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            clock.now = Instant.parse("2026-10-09T23:00:00Z");
            when(provider.isConfigured()).thenReturn(false);
            when(snapshots.load(anySet())).thenReturn(Map.of("AAPL", saved("AAPL", Instant.parse("2026-10-09T04:13:00Z"))));
            var result = service.getQuotes(Set.of("AAPL")).get("AAPL");
            assertThat(result.status()).isEqualTo(STALE);
            assertThat(result.quote().confirmedClose()).isFalse();
            assertThat(output.getAll()).contains("policy=ON_DEMAND", "enabled=true", "marketOpen=false", "eodSession=2026-10-09",
                    "configured=false", "batch=0", "reason=UNCONFIGURED", "retained=true", "origin=PERSISTED");
            verify(provider, never()).getClosingQuotes(anySet(), any());
            verify(snapshots, never()).save(anyMap(), any());
        } finally { logger.setLevel(previous); }
    }
    private MarketQuoteSnapshotStore.Snapshot closingSaved(String symbol, Instant fetchedAt) {
        return new MarketQuoteSnapshotStore.Snapshot(new MarketQuote(symbol, new BigDecimal("210.25"), "USD", null, null,
                UsEquityMarketSession.latestCompletedSession(clock.instant()).date(), true), fetchedAt);
    }
    private MarketQuoteSnapshotStore.Snapshot saved(String symbol, Instant fetchedAt) {
        return new MarketQuoteSnapshotStore.Snapshot(new MarketQuote(symbol, new BigDecimal("210.25"), "USD",
                new BigDecimal("209"), fetchedAt.minusSeconds(60)), fetchedAt);
    }
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T16:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
