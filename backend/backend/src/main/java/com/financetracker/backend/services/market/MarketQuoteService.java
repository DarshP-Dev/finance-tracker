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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Shared symbol cache and process-local credit guard. Concurrent misses share one batch. */
@Service @RequiredArgsConstructor
public class MarketQuoteService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketQuoteService.class);
    public enum QuotePolicy { ON_DEMAND, CACHE_ONLY, MISSING_ONLY }
    public record QuoteResult(MarketQuote quote, MarketDataProvider.Status status, Instant fetchedAt) {}
    public record RefreshResult(Map<String, QuoteResult> quotes, boolean accepted, int retryAfterSeconds) {}
    public record Usage(long providerRequests, long symbolsRequested, long cacheHits, long cacheMisses,
                        long staleCacheHits, long quotaBlocks, long cooldownBlocks, long backoffBlocks,
                        int lastBatchSize, int minuteCredits, int dayCredits, int minuteRequests, int dayRequests) {}
    private record Entry(MarketQuote quote, MarketDataProvider.Status failure, Instant fetchedAt, Instant nextAttempt) {}
    private final MarketDataProvider provider;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private Instant minute;
    private LocalDate day;
    private int minuteCredits, dayCredits;
    private int minuteRequests, dayRequests, lastBatchSize;
    private long providerRequests, symbolsRequested, cacheHits, cacheMisses, staleCacheHits,
            quotaBlocks, cooldownBlocks, backoffBlocks;
    private Instant nextManualRefreshAt, backoffUntil;

    public synchronized Map<String, QuoteResult> getQuotes(Set<String> requested) {
        return getQuotes(requested, QuotePolicy.ON_DEMAND);
    }

    /** Manual refresh shares the authoritative cache; it never forces fresh quotes out. */
    public synchronized RefreshResult refreshQuotes(Set<String> requested) {
        if (requested.isEmpty()) return new RefreshResult(Map.of(), true, 0);
        Instant now = clock.instant();
        if (nextManualRefreshAt != null && now.isBefore(nextManualRefreshAt)) {
            cooldownBlocks++;
            LOGGER.debug("Market refresh skipped: shared cooldown active");
            return new RefreshResult(getQuotes(requested, QuotePolicy.CACHE_ONLY), false,
                    (int) ((Duration.between(now, nextManualRefreshAt).toMillis() + 999) / 1000));
        }
        int cooldown = (int) bounded(properties.getRefreshCooldown(), 120, 60, 3600).toSeconds();
        nextManualRefreshAt = now.plusSeconds(cooldown);
        return new RefreshResult(getQuotes(requested), true, cooldown);
    }

    public synchronized Usage usage() {
        return new Usage(providerRequests, symbolsRequested, cacheHits, cacheMisses, staleCacheHits,
                quotaBlocks, cooldownBlocks, backoffBlocks, lastBatchSize,
                minuteCredits, dayCredits, minuteRequests, dayRequests);
    }

    public synchronized Map<String, QuoteResult> getQuotes(Set<String> requested, QuotePolicy policy) {
        Set<String> symbols = new LinkedHashSet<>();
        requested.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).sorted().forEach(symbols::add);
        Map<String, QuoteResult> results = new LinkedHashMap<>();
        if (symbols.isEmpty()) return results;
        var unavailable = !properties.isEnabled() ? DISABLED : !provider.isConfigured() ? UNAVAILABLE : null;
        if (unavailable != null) { symbols.forEach(s -> results.put(s, new QuoteResult(null, unavailable, null))); return results; }
        Instant now = clock.instant();
        Instant currentMinute = now.truncatedTo(ChronoUnit.MINUTES);
        LocalDate currentDay = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!currentMinute.equals(minute)) { minute = currentMinute; minuteCredits = 0; minuteRequests = 0; }
        if (!currentDay.equals(day)) { day = currentDay; dayCredits = 0; dayRequests = 0; }
        Duration ttl = bounded(properties.getCacheDuration(), 600, 60, 3600);
        Duration stale = bounded(properties.getStaleDuration(), 86400, ttl.toSeconds(), 172800);
        long hitsBefore = cacheHits, missesBefore = cacheMisses;
        Set<String> misses = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (!symbol.matches("[A-Z0-9][A-Z0-9.-]{0,19}")) { results.put(symbol, new QuoteResult(null, INVALID_SYMBOL, null)); continue; }
            Entry entry = cache.get(symbol);
            boolean cachedOnly = policy == QuotePolicy.CACHE_ONLY;
            boolean usableStale = entry != null && entry.quote() != null && entry.fetchedAt().plus(stale).isAfter(now);
            if (entry != null && (cachedOnly || now.isBefore(entry.nextAttempt())
                    || (usableStale && now.isBefore(entry.fetchedAt().plus(bounded(properties.getRefreshCooldown(), 120, 60, 3600))))
                    || (policy == QuotePolicy.MISSING_ONLY && usableStale))) {
                results.put(symbol, result(entry, now, ttl, stale));
                cacheHits++;
            } else {
                cacheMisses++;
                if (cachedOnly) results.put(symbol, new QuoteResult(null, UNAVAILABLE, null));
                else misses.add(symbol);
            }
        }
        int dailyBudget = Math.clamp(properties.getCreditsPerDay(), 1, 1_000_000);
        int safeDailyBudget = dailyBudget - Math.clamp(properties.getDailyReserve(), 0, dailyBudget);
        int remaining = Math.max(0, Math.min(Math.clamp(properties.getCreditsPerMinute(), 1, 10000) - minuteCredits,
                safeDailyBudget - dayCredits));
        boolean backingOff = backoffUntil != null && now.isBefore(backoffUntil);
        boolean requestBudgetReached = minuteRequests >= Math.clamp(properties.getMaxRequestsPerMinute(), 1, 10000)
                || dayRequests >= Math.clamp(properties.getMaxRequestsPerDay(), 1, 1_000_000);
        if (!misses.isEmpty() && backingOff) { backoffBlocks++; remaining = 0; }
        else if (!misses.isEmpty() && requestBudgetReached) remaining = 0;
        if (!misses.isEmpty() && !backingOff && misses.size() > remaining) quotaBlocks++;
        Set<String> batch = new LinkedHashSet<>(misses.stream().limit(remaining).toList());
        Map<String, MarketDataProvider.Result> fetched = Map.of();
        if (!batch.isEmpty()) {
            minuteCredits += batch.size(); dayCredits += batch.size();
            minuteRequests++; dayRequests++; providerRequests++; symbolsRequested += batch.size(); lastBatchSize = batch.size();
            LOGGER.debug("Market data batch: {} symbols; daily credits {}/{} (reserve {})",
                    batch.size(), dayCredits, safeDailyBudget, properties.getDailyReserve());
            try { fetched = provider.getQuotes(batch); } catch (RuntimeException exception) { /* fixed fallback, no provider details */ }
            if (fetched == null) fetched = Map.of();
        }
        Instant completed = clock.instant();
        var completedResults = fetched;
        // A provider-wide rejection/outage must also suppress requests for different symbols.
        if (!batch.isEmpty() && (fetched.values().stream().anyMatch(r -> r != null && r.status() == RATE_LIMITED)
                || batch.stream().allMatch(s -> fetchedStatus(completedResults.get(s)) == UNAVAILABLE))) {
            backoffUntil = completed.plus(bounded(properties.getProviderBackoff(), 300, 60, 3600));
            LOGGER.debug("Market data provider backoff activated");
        }
        for (String symbol : misses) {
            var fetchedResult = batch.contains(symbol) ? fetched.get(symbol) : MarketDataProvider.Result.unavailable(RATE_LIMITED);
            if (fetchedResult == null) fetchedResult = MarketDataProvider.Result.unavailable(UNAVAILABLE);
            Entry old = cache.get(symbol);
            Entry entry;
            var quote = fetchedResult.quote();
            if (fetchedResult.status() == AVAILABLE && quote != null && symbol.equals(quote.symbol())
                    && quote.currentPrice() != null && quote.currentPrice().signum() > 0 && "USD".equals(quote.currency())) {
                // Repeated identical market timestamps often mean last-close data. Recheck less often,
                // but freshness/status still uses the normal TTL and original retrieval time.
                boolean unchanged = old != null && old.quote() != null && quote.marketTimestamp() != null
                        && quote.marketTimestamp().equals(old.quote().marketTimestamp())
                        && quote.currentPrice().compareTo(old.quote().currentPrice()) == 0;
                Duration delay = unchanged ? bounded(properties.getUnchangedQuoteCacheDuration(), 1800, ttl.toSeconds(), 86400) : ttl;
                entry = new Entry(quote, null, completed, completed.plus(delay));
            } else {
                var failure = fetchedResult.status() == AVAILABLE ? UNAVAILABLE : fetchedResult.status();
                boolean retain = old != null && old.quote() != null && old.fetchedAt().plus(stale).isAfter(completed)
                        && (failure == UNAVAILABLE || failure == RATE_LIMITED);
                Instant nextAttempt = failure == RATE_LIMITED ? completed.plusSeconds(60)
                        : retain ? completed.plus(bounded(properties.getProviderBackoff(), 300, 60, 3600))
                        : completed.plus(bounded(properties.getNegativeCacheDuration(), 1800, 60, 86400));
                if (backoffUntil != null && completed.isBefore(backoffUntil) && nextAttempt.isBefore(backoffUntil)) nextAttempt = backoffUntil;
                entry = new Entry(retain ? old.quote() : null, failure, retain ? old.fetchedAt() : null, nextAttempt);
            }
            cache.put(symbol, entry);
            results.put(symbol, result(entry, completed, ttl, stale));
        }
        int maximum = Math.clamp(properties.getMaxCacheSymbols(), 1, 10000);
        while (cache.size() > maximum) cache.remove(cache.keySet().iterator().next());
        long staleHits = results.values().stream().filter(r -> r.status() == STALE).count();
        staleCacheHits += staleHits;
        LOGGER.debug("Market data cache: {} reused, {} missing/expired, {} stale; policy {}",
                cacheHits - hitsBefore, cacheMisses - missesBefore, staleHits, policy);
        return results;
    }
    private MarketDataProvider.Status fetchedStatus(MarketDataProvider.Result result) {
        return result == null ? UNAVAILABLE : result.status();
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
