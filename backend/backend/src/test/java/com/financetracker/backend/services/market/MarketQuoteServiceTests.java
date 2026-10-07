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
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T12:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
