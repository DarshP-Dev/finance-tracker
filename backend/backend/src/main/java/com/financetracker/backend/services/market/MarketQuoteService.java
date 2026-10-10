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
                         Instant nextAttempt, QuoteOrigin origin, LocalDate closeAttemptSession) {}
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
        Instant now = clock.instant();
        Instant currentMinute = now.truncatedTo(ChronoUnit.MINUTES);
        LocalDate currentDay = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!currentMinute.equals(minute)) { minute = currentMinute; minuteCredits = 0; minuteRequests = 0; }
        if (!currentDay.equals(day)) { day = currentDay; dayCredits = 0; dayRequests = 0; }
        Duration ttl = bounded(properties.getCacheDuration(), 600, 60, 3600);
        Duration stale = bounded(properties.getStaleDuration(), 86400, ttl.toSeconds(), 172800);
        boolean marketOpen = UsEquityMarketSession.isOpen(now);
        var completedSession = UsEquityMarketSession.latestCompletedSession(now);
        boolean mayFetch = properties.isEnabled() && (marketOpen || !now.isBefore(completedSession.closesAt()
                .plus(bounded(properties.getClosePublicationDelay(), 900, 0, 7200))));
        LOGGER.debug("Market quote resolution: policy={}, enabled={}, marketOpen={}, eodSession={}, mayFetch={}, symbols={}",
                policy, properties.isEnabled(), marketOpen, completedSession.date(), mayFetch, symbols.size());
        Duration persistedAge = Duration.ofDays(Math.clamp(properties.getPersistedMaxAgeDays(), 1, 365));
        Duration memoryAge = marketOpen ? stale : persistedAge;
        long hitsBefore = cacheHits, missesBefore = cacheMisses;
        Set<String> misses = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (!symbol.matches("[A-Z0-9][A-Z0-9.-]{0,19}")) { results.put(symbol, new QuoteResult(null, INVALID_SYMBOL, null)); continue; }
            Entry entry = cache.get(symbol);
            boolean cachedOnly = policy == QuotePolicy.CACHE_ONLY;
            boolean usableStale = usable(entry, now, entry != null && entry.origin() == QuoteOrigin.PERSISTED ? persistedAge : memoryAge);
            boolean currentSession = entry != null && currentIntraday(entry.quote(), now);
            boolean retryCooling = entry != null && entry.failure() != null && now.isBefore(entry.nextAttempt())
                    && (marketOpen ? entry.closeAttemptSession() == null : completedSession.date().equals(entry.closeAttemptSession()));
            if (entry != null && ((!marketOpen && latestClose(entry.quote(), completedSession.date())) || (cachedOnly && usableStale)
                    || retryCooling
                    || (marketOpen && usableStale && entry.origin() == QuoteOrigin.PROVIDER && currentSession && now.isBefore(entry.nextAttempt()))
                    || (marketOpen && usableStale && entry.origin() == QuoteOrigin.PROVIDER && currentSession
                        && now.isBefore(entry.fetchedAt().plus(bounded(properties.getRefreshCooldown(), 120, 60, 3600))))
                    || (policy == QuotePolicy.MISSING_ONLY && usableStale))) {
                results.put(symbol, result(entry, now, ttl, entry.origin() == QuoteOrigin.PERSISTED ? persistedAge : memoryAge, marketOpen));
                LOGGER.debug("Market quote reuse: reason={}, origin={}, session={}, confirmedClose={}, nextAttempt={}",
                        !marketOpen && latestClose(entry.quote(), completedSession.date()) ? "LATEST_SESSION_CLOSE"
                                : cachedOnly ? "CACHE_ONLY" : retryCooling ? "RETRY_COOLDOWN"
                                : policy == QuotePolicy.MISSING_ONLY ? "MISSING_ONLY" : "INTRADAY_CACHE",
                        entry.origin(), entry.quote() == null ? null : entry.quote().sessionDate(),
                        entry.quote() != null && entry.quote().confirmedClose(), entry.nextAttempt());
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
                LOGGER.debug("Market snapshot candidate: found={}, session={}, confirmedClose={}, fetchedAt={}",
                        saved != null, saved == null || saved.quote() == null ? null : saved.quote().sessionDate(),
                        saved != null && saved.quote() != null && saved.quote().confirmedClose(), saved == null ? null : saved.fetchedAt());
                if (saved != null && valid(symbol, saved.quote()) && saved.fetchedAt() != null
                        && !saved.fetchedAt().isAfter(now) && (saved.fetchedAt().plus(persistedAge).isAfter(now)
                            || (!marketOpen && latestClose(saved.quote(), completedSession.date())))) {
                    Entry old = cache.get(symbol);
                    if (old == null || old.quote() == null || saved.quote().compareMarketData(old.quote()) > 0
                            || (saved.quote().compareMarketData(old.quote()) == 0 && !old.fetchedAt().isAfter(saved.fetchedAt()))) {
                        Instant next = old != null && old.failure() != null ? old.nextAttempt() : now;
                        cache.put(symbol, new Entry(saved.quote(), old == null ? null : old.failure(), saved.fetchedAt(), next,
                                QuoteOrigin.PERSISTED, old == null ? null : old.closeAttemptSession()));
                    }
                    if ((!marketOpen && latestClose(cache.get(symbol).quote(), completedSession.date()))
                            || policy == QuotePolicy.CACHE_ONLY || policy == QuotePolicy.MISSING_ONLY) {
                        results.put(symbol, result(cache.get(symbol), now, ttl, persistedAge, marketOpen));
                        misses.remove(symbol);
                    }
                }
                if ((policy == QuotePolicy.CACHE_ONLY || !mayFetch) && misses.remove(symbol)) {
                    LOGGER.debug("Market quote fetch skipped: reason={}", policy == QuotePolicy.CACHE_ONLY ? "CACHE_ONLY"
                            : !properties.isEnabled() ? "DISABLED" : "PUBLICATION_DELAY");
                    Entry old = cache.get(symbol);
                    results.put(symbol, old == null ? new QuoteResult(null, properties.isEnabled() ? UNAVAILABLE : DISABLED, null)
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
        if (!misses.isEmpty()) {
            LOGGER.debug("Market provider decision: mode={}, eodSession={}, configured={}, missing={}, batch={}, reason={}, minuteCredits={}, dayCredits={}",
                    marketOpen ? "QUOTE" : "EOD", completedSession.date(), configured, misses.size(), batch.size(),
                    !configured ? "UNCONFIGURED" : backingOff ? "PROVIDER_BACKOFF" : requestBudgetReached ? "REQUEST_LIMIT"
                            : batch.size() < misses.size() ? "CREDIT_QUOTA" : "ELIGIBLE", minuteCredits, dayCredits);
            if (!configured) LOGGER.warn("Market provider request skipped: provider is unconfigured; eligible last-known quotes will be used");
        }
        Map<String, MarketDataProvider.Result> fetched = Map.of();
        if (!batch.isEmpty()) {
            minuteCredits += batch.size(); dayCredits += batch.size();
            minuteRequests++; dayRequests++; providerRequests++; symbolsRequested += batch.size(); lastBatchSize = batch.size();
            LOGGER.debug("Market data batch: {} symbols; daily credits {}/{} (reserve {})",
                    batch.size(), dayCredits, safeDailyBudget, properties.getDailyReserve());
            try { fetched = marketOpen ? provider.getQuotes(batch) : provider.getClosingQuotes(batch, completedSession.date()); }
            catch (RuntimeException exception) { LOGGER.debug("Market provider invocation failed: exceptionType={}", exception.getClass().getSimpleName()); }
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
            if ((fetchedResult.status() == AVAILABLE || fetchedResult.status() == STALE) && valid(symbol, quote)) {
                boolean newer = old == null || quote.compareMarketData(old.quote()) >= 0;
                boolean current = fetchedResult.status() == AVAILABLE && (marketOpen ? currentIntraday(quote, completed)
                        : latestClose(quote, completedSession.date()));
                Duration delay = current ? ttl : outdatedCooldown(ttl);
                entry = newer ? new Entry(quote, current ? null : STALE, completed, completed.plus(delay), QuoteOrigin.PROVIDER,
                        marketOpen ? null : completedSession.date())
                        : new Entry(old.quote(), STALE, old.fetchedAt(), completed.plus(outdatedCooldown(ttl)), old.origin(),
                            marketOpen ? null : completedSession.date());
                if (newer) successful.put(symbol, new MarketQuoteSnapshotStore.Snapshot(quote, completed));
                LOGGER.debug("Market provider quote evaluated: status={}, session={}, confirmedClose={}, acceptedForPersistence={}, current={}",
                        fetchedResult.status(), quote.sessionDate(), quote.confirmedClose(), newer, current);
            } else {
                var failure = fetchedResult.status() == AVAILABLE ? UNAVAILABLE : fetchedResult.status();
                boolean retain = usable(old, completed, old != null && old.origin() == QuoteOrigin.PERSISTED ? persistedAge : memoryAge)
                        && (failure == UNAVAILABLE || failure == RATE_LIMITED);
                Instant nextAttempt = !marketOpen ? completed.plus(outdatedCooldown(ttl)) : failure == RATE_LIMITED ? completed.plusSeconds(60)
                        : retain ? completed.plus(bounded(properties.getProviderBackoff(), 300, 60, 3600))
                        : completed.plus(bounded(properties.getNegativeCacheDuration(), 1800, 60, 86400));
                if (backoffUntil != null && completed.isBefore(backoffUntil) && nextAttempt.isBefore(backoffUntil)) nextAttempt = backoffUntil;
                entry = new Entry(retain ? old.quote() : null, failure, retain ? old.fetchedAt() : null, nextAttempt,
                        retain ? old.origin() : QuoteOrigin.PROVIDER, marketOpen ? null : completedSession.date());
                LOGGER.debug("Market quote fallback: reason={}, retained={}, origin={}, nextAttempt={}",
                        !configured ? "UNCONFIGURED" : !batch.contains(symbol) ? "LOCAL_GUARD" : failure,
                        retain, entry.origin(), nextAttempt);
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
    private boolean latestClose(MarketQuote quote, LocalDate sessionDate) {
        return quote != null && quote.confirmedClose() && sessionDate.equals(quote.sessionDate());
    }
    private boolean currentIntraday(MarketQuote quote, Instant now) {
        return quote != null && !quote.confirmedClose() && quote.marketTimestamp() != null
                && !quote.marketTimestamp().isAfter(now) && UsEquityMarketSession.easternDate(now).equals(quote.sessionDate());
    }
    private Duration outdatedCooldown(Duration ttl) {
        return bounded(properties.getUnchangedQuoteCacheDuration(), 1800, ttl.toSeconds(), 86400);
    }
    private boolean usable(Entry entry, Instant now, Duration maximumAge) {
        return entry != null && entry.quote() != null && entry.fetchedAt() != null
                && !entry.fetchedAt().isAfter(now) && entry.fetchedAt().plus(maximumAge).isAfter(now);
    }
    private QuoteResult result(Entry entry, Instant now, Duration ttl, Duration stale, boolean marketOpen) {
        if (!usable(entry, now, stale) && !(entry.quote() != null && entry.fetchedAt() != null && !entry.fetchedAt().isAfter(now)
                && !marketOpen && latestClose(entry.quote(), UsEquityMarketSession.latestCompletedSession(now).date())))
            return new QuoteResult(null, !properties.isEnabled() ? DISABLED : entry.failure() == null ? UNAVAILABLE : entry.failure(), null);
        return new QuoteResult(entry.quote(), !properties.isEnabled() || !marketOpen || entry.origin() == QuoteOrigin.PERSISTED
                || !currentIntraday(entry.quote(), now)
                || entry.failure() != null || !entry.fetchedAt().plus(ttl).isAfter(now) ? STALE : AVAILABLE, entry.fetchedAt());
    }
    private Duration bounded(Duration value, long fallback, long min, long max) {
        return Duration.ofSeconds(Math.clamp(value == null ? fallback : value.toSeconds(), min, max));
    }
}
