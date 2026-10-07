package com.financetracker.backend.services.market;

import static com.financetracker.backend.services.market.MarketDataProvider.Status.*;
import com.financetracker.backend.config.MarketDataProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Shared symbol cache and process-local credit guard. Concurrent misses share one batch. */
@Service @RequiredArgsConstructor
public class MarketQuoteService {
    public record QuoteResult(MarketQuote quote, MarketDataProvider.Status status, Instant fetchedAt) {}
    private record Entry(MarketQuote quote, MarketDataProvider.Status failure, Instant fetchedAt, Instant nextAttempt) {}
    private final MarketDataProvider provider;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private Instant minute;
    private LocalDate day;
    private int minuteCredits, dayCredits;

    public synchronized Map<String, QuoteResult> getQuotes(Set<String> requested) {
        Set<String> symbols = new LinkedHashSet<>();
        requested.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).sorted().forEach(symbols::add);
        Map<String, QuoteResult> results = new LinkedHashMap<>();
        var unavailable = !properties.isEnabled() ? DISABLED : !provider.isConfigured() ? UNAVAILABLE : null;
        if (unavailable != null) { symbols.forEach(s -> results.put(s, new QuoteResult(null, unavailable, null))); return results; }
        Instant now = clock.instant();
        Instant currentMinute = now.truncatedTo(ChronoUnit.MINUTES);
        LocalDate currentDay = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!currentMinute.equals(minute)) { minute = currentMinute; minuteCredits = 0; }
        if (!currentDay.equals(day)) { day = currentDay; dayCredits = 0; }
        Duration ttl = bounded(properties.getCacheDuration(), 600, 60, 3600);
        Duration stale = bounded(properties.getStaleDuration(), 86400, ttl.toSeconds(), 172800);
        Set<String> misses = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (!symbol.matches("[A-Z0-9][A-Z0-9.-]{0,19}")) { results.put(symbol, new QuoteResult(null, INVALID_SYMBOL, null)); continue; }
            Entry entry = cache.get(symbol);
            if (entry != null && now.isBefore(entry.nextAttempt())) {
                results.put(symbol, result(entry, now, ttl, stale));
            } else misses.add(symbol);
        }
        int remaining = Math.max(0, Math.min(Math.clamp(properties.getCreditsPerMinute(), 1, 10000) - minuteCredits,
                Math.clamp(properties.getCreditsPerDay(), 1, 1_000_000) - dayCredits));
        Set<String> batch = new LinkedHashSet<>(misses.stream().limit(remaining).toList());
        Map<String, MarketDataProvider.Result> fetched = Map.of();
        if (!batch.isEmpty()) {
            minuteCredits += batch.size(); dayCredits += batch.size();
            try { fetched = provider.getQuotes(batch); } catch (RuntimeException exception) { /* fixed fallback, no provider details */ }
        }
        Instant completed = clock.instant();
        for (String symbol : misses) {
            var fetchedResult = batch.contains(symbol) ? fetched.get(symbol) : MarketDataProvider.Result.unavailable(RATE_LIMITED);
            if (fetchedResult == null) fetchedResult = MarketDataProvider.Result.unavailable(UNAVAILABLE);
            Entry old = cache.get(symbol);
            Entry entry;
            var quote = fetchedResult.quote();
            if (fetchedResult.status() == AVAILABLE && quote != null && symbol.equals(quote.symbol())
                    && quote.currentPrice() != null && quote.currentPrice().signum() > 0 && "USD".equals(quote.currency())) {
                entry = new Entry(quote, null, completed, completed.plus(ttl));
            } else {
                var failure = fetchedResult.status() == AVAILABLE ? UNAVAILABLE : fetchedResult.status();
                boolean retain = old != null && old.quote() != null && old.fetchedAt().plus(stale).isAfter(completed)
                        && (failure == UNAVAILABLE || failure == RATE_LIMITED);
                entry = new Entry(retain ? old.quote() : null, failure, retain ? old.fetchedAt() : null, completed.plusSeconds(60));
            }
            cache.put(symbol, entry);
            results.put(symbol, result(entry, completed, ttl, stale));
        }
        int maximum = Math.clamp(properties.getMaxCacheSymbols(), 1, 10000);
        while (cache.size() > maximum) cache.remove(cache.keySet().iterator().next());
        return results;
    }
    private QuoteResult result(Entry entry, Instant now, Duration ttl, Duration stale) {
        if (entry.quote() == null || !entry.fetchedAt().plus(stale).isAfter(now))
            return new QuoteResult(null, entry.failure() == null ? UNAVAILABLE : entry.failure(), null);
        return new QuoteResult(entry.quote(), entry.failure() != null || !entry.fetchedAt().plus(ttl).isAfter(now) ? STALE : AVAILABLE, entry.fetchedAt());
    }
    private Duration bounded(Duration value, long fallback, long min, long max) {
        return Duration.ofSeconds(Math.clamp(value == null ? fallback : value.toSeconds(), min, max));
    }
}
