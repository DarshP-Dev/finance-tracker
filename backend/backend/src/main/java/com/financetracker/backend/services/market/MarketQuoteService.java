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

/** Shared L1 cache, lazy PostgreSQL L2 snapshots and process-local credit guard. */
@Service @RequiredArgsConstructor
public class MarketQuoteService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketQuoteService.class);
    public enum QuotePolicy { ON_DEMAND, CACHE_ONLY, MISSING_ONLY }
    public record QuoteResult(MarketQuote quote, MarketDataProvider.Status status, Instant fetchedAt) {}
    public record RefreshResult(Map<String, QuoteResult> quotes, boolean accepted, int retryAfterSeconds) {}
    public record Usage(long providerRequests, long symbolsRequested, long cacheHits, long cacheMisses,
                        long staleCacheHits, long quotaBlocks, long cooldownBlocks, long backoffBlocks,
                        int lastBatchSize, int minuteCredits, int dayCredits, int minuteRequests, int dayRequests) {}
    public enum QuoteOrigin { PROVIDER, PERSISTED }
    private record Entry(MarketQuote quote, MarketDataProvider.Status failure, Instant fetchedAt,
                         Instant nextAttempt, QuoteOrigin origin) {}
    private final MarketDataProvider provider;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final MarketQuoteSnapshotStore snapshots;
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
        if (!properties.isEnabled()) {
            symbols.forEach(s -> results.put(s, new QuoteResult(null, DISABLED, null))); return results;
        }
        Instant now = clock.instant();
        Instant currentMinute = now.truncatedTo(ChronoUnit.MINUTES);
        LocalDate currentDay = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!currentMinute.equals(minute)) { minute = currentMinute; minuteCredits = 0; minuteRequests = 0; }
        if (!currentDay.equals(day)) { day = currentDay; dayCredits = 0; dayRequests = 0; }
        Duration ttl = bounded(properties.getCacheDuration(), 600, 60, 3600);
        Duration stale = bounded(properties.getStaleDuration(), 86400, ttl.toSeconds(), 172800);
        boolean marketOpen = UsEquityMarketSession.isOpen(now);
        Duration persistedAge = Duration.ofDays(Math.clamp(properties.getPersistedMaxAgeDays(), 1, 365));
        Duration memoryAge = marketOpen ? stale : persistedAge;
        long hitsBefore = cacheHits, missesBefore = cacheMisses;
        Set<String> misses = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (!symbol.matches("[A-Z0-9][A-Z0-9.-]{0,19}")) { results.put(symbol, new QuoteResult(null, INVALID_SYMBOL, null)); continue; }
            Entry entry = cache.get(symbol);
            boolean cachedOnly = policy == QuotePolicy.CACHE_ONLY;
            boolean usableStale = usable(entry, now, entry != null && entry.origin() == QuoteOrigin.PERSISTED ? persistedAge : memoryAge);
            // A closed-session quote must be refreshed once a new session opens, even if its TTL remains.
            boolean currentSession = !marketOpen || (entry != null && entry.fetchedAt() != null
                    && !entry.fetchedAt().isBefore(UsEquityMarketSession.latestSessionOpen(now)));
            if (entry != null && ((!marketOpen && usableStale) || (cachedOnly && usableStale)
                    || (now.isBefore(entry.nextAttempt()) && (entry.failure() != null || (entry.origin() == QuoteOrigin.PROVIDER && currentSession)))
                    || (usableStale && entry.origin() == QuoteOrigin.PROVIDER && currentSession
                        && now.isBefore(entry.fetchedAt().plus(bounded(properties.getRefreshCooldown(), 120, 60, 3600))))
                    || (policy == QuotePolicy.MISSING_ONLY && usableStale && currentSession))) {
                results.put(symbol, result(entry, now, ttl, entry.origin() == QuoteOrigin.PERSISTED ? persistedAge : memoryAge, marketOpen));
                cacheHits++;
            } else {
                cacheMisses++;
                misses.add(symbol);
            }
        }
        // One batch DB lookup only for symbols not adequately resolved by memory.
        if (!misses.isEmpty()) {
            Map<String, MarketQuoteSnapshotStore.Snapshot> persisted = Map.of();
            try { persisted = snapshots.load(Set.copyOf(misses)); }
            catch (RuntimeException exception) { LOGGER.warn("Market quote snapshot lookup failed ({})", exception.getClass().getSimpleName()); }
            for (String symbol : new LinkedHashSet<>(misses)) {
                var saved = persisted.get(symbol);
                if (saved != null && valid(symbol, saved.quote()) && saved.fetchedAt() != null
                        && !saved.fetchedAt().isAfter(now) && saved.fetchedAt().plus(persistedAge).isAfter(now)) {
                    Entry old = cache.get(symbol);
                    if (old == null || old.quote() == null || !old.fetchedAt().isAfter(saved.fetchedAt())) {
                        Instant next = old != null && old.failure() != null ? old.nextAttempt() : now;
                        cache.put(symbol, new Entry(saved.quote(), old == null ? null : old.failure(), saved.fetchedAt(), next, QuoteOrigin.PERSISTED));
                    }
                    if (!marketOpen || policy == QuotePolicy.CACHE_ONLY || policy == QuotePolicy.MISSING_ONLY) {
                        results.put(symbol, result(cache.get(symbol), now, ttl, persistedAge, marketOpen));
                        misses.remove(symbol);
                    }
                }
                if (policy == QuotePolicy.CACHE_ONLY && misses.remove(symbol)) {
                    Entry old = cache.get(symbol);
                    results.put(symbol, old == null ? new QuoteResult(null, UNAVAILABLE, null)
                            : result(old, now, ttl, old.origin() == QuoteOrigin.PERSISTED ? persistedAge : memoryAge, marketOpen));
                }
            }
        }
        // Cache-only Insights/Gemini may read L2 but must never initiate external HTTP.
        boolean configured = !misses.isEmpty() && provider.isConfigured();
        int dailyBudget = Math.clamp(properties.getCreditsPerDay(), 1, 1_000_000);
        int safeDailyBudget = dailyBudget - Math.clamp(properties.getDailyReserve(), 0, dailyBudget);
        int remaining = Math.max(0, Math.min(Math.clamp(properties.getCreditsPerMinute(), 1, 10000) - minuteCredits,
                safeDailyBudget - dayCredits));
        if (!configured) remaining = 0;
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
        Map<String, MarketQuoteSnapshotStore.Snapshot> successful = new LinkedHashMap<>();
        var completedResults = fetched;
        // A provider-wide rejection/outage must also suppress requests for different symbols.
        if (!batch.isEmpty() && (fetched.values().stream().anyMatch(r -> r != null && r.status() == RATE_LIMITED)
                || batch.stream().allMatch(s -> fetchedStatus(completedResults.get(s)) == UNAVAILABLE))) {
            backoffUntil = completed.plus(bounded(properties.getProviderBackoff(), 300, 60, 3600));
            LOGGER.debug("Market data provider backoff activated");
        }
        for (String symbol : misses) {
            var fetchedResult = batch.contains(symbol) ? fetched.get(symbol) : MarketDataProvider.Result.unavailable(configured ? RATE_LIMITED : UNAVAILABLE);
            if (fetchedResult == null) fetchedResult = MarketDataProvider.Result.unavailable(UNAVAILABLE);
            Entry old = cache.get(symbol);
            Entry entry;
            var quote = fetchedResult.quote();
            if (fetchedResult.status() == AVAILABLE && valid(symbol, quote)) {
                // Repeated identical market timestamps often mean last-close data. Recheck less often,
                // but freshness/status still uses the normal TTL and original retrieval time.
                boolean unchanged = old != null && old.quote() != null && quote.marketTimestamp() != null
                        && old.origin() == QuoteOrigin.PROVIDER
                        && (!marketOpen || !old.fetchedAt().isBefore(UsEquityMarketSession.latestSessionOpen(now)))
                        && quote.marketTimestamp().equals(old.quote().marketTimestamp())
                        && quote.currentPrice().compareTo(old.quote().currentPrice()) == 0;
                Duration delay = unchanged ? bounded(properties.getUnchangedQuoteCacheDuration(), 1800, ttl.toSeconds(), 86400) : ttl;
                entry = new Entry(quote, null, completed, completed.plus(delay), QuoteOrigin.PROVIDER);
                successful.put(symbol, new MarketQuoteSnapshotStore.Snapshot(quote, completed));
            } else {
                var failure = fetchedResult.status() == AVAILABLE ? UNAVAILABLE : fetchedResult.status();
                boolean retain = usable(old, completed, old != null && old.origin() == QuoteOrigin.PERSISTED ? persistedAge : memoryAge)
                        && (failure == UNAVAILABLE || failure == RATE_LIMITED);
                Instant nextAttempt = failure == RATE_LIMITED ? completed.plusSeconds(60)
                        : retain ? completed.plus(bounded(properties.getProviderBackoff(), 300, 60, 3600))
                        : completed.plus(bounded(properties.getNegativeCacheDuration(), 1800, 60, 86400));
                if (backoffUntil != null && completed.isBefore(backoffUntil) && nextAttempt.isBefore(backoffUntil)) nextAttempt = backoffUntil;
                entry = new Entry(retain ? old.quote() : null, failure, retain ? old.fetchedAt() : null, nextAttempt,
                        retain ? old.origin() : QuoteOrigin.PROVIDER);
            }
            cache.put(symbol, entry);
            results.put(symbol, result(entry, completed, ttl, entry.origin() == QuoteOrigin.PERSISTED ? persistedAge : memoryAge, marketOpen));
        }
        if (!successful.isEmpty()) {
            try { snapshots.save(successful, completed); }
            catch (RuntimeException exception) { LOGGER.warn("Market quote snapshot persistence failed ({})", exception.getClass().getSimpleName()); }
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
    private boolean valid(String symbol, MarketQuote quote) {
        return quote != null && symbol.equals(quote.symbol()) && quote.currentPrice() != null
                && quote.currentPrice().signum() > 0 && quote.currentPrice().precision() <= 24
                && quote.currentPrice().scale() <= 12 && "USD".equals(quote.currency());
    }
    private boolean usable(Entry entry, Instant now, Duration maximumAge) {
        return entry != null && entry.quote() != null && entry.fetchedAt() != null
                && !entry.fetchedAt().isAfter(now) && entry.fetchedAt().plus(maximumAge).isAfter(now);
    }
    private QuoteResult result(Entry entry, Instant now, Duration ttl, Duration stale, boolean marketOpen) {
        if (!usable(entry, now, stale))
            return new QuoteResult(null, entry.failure() == null ? UNAVAILABLE : entry.failure(), null);
        return new QuoteResult(entry.quote(), !marketOpen || entry.origin() == QuoteOrigin.PERSISTED
                || entry.fetchedAt().isBefore(UsEquityMarketSession.latestSessionOpen(now))
                || entry.failure() != null || !entry.fetchedAt().plus(ttl).isAfter(now) ? STALE : AVAILABLE, entry.fetchedAt());
    }
    private Duration bounded(Duration value, long fallback, long min, long max) {
        return Duration.ofSeconds(Math.clamp(value == null ? fallback : value.toSeconds(), min, max));
    }
}
