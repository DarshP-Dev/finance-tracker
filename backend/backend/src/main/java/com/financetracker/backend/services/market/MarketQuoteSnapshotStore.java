package com.financetracker.backend.services.market;

import com.financetracker.backend.repositories.MarketQuoteSnapshotRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Short, independent DB transactions; never holds a transaction during provider HTTP. */
@Service @RequiredArgsConstructor
public class MarketQuoteSnapshotStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketQuoteSnapshotStore.class);
    public record Snapshot(MarketQuote quote, Instant fetchedAt) {}
    private final MarketQuoteSnapshotRepository repository;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<String, Snapshot> load(Set<String> symbols) {
        if (symbols.isEmpty()) return Map.of();
        var result = new LinkedHashMap<String, Snapshot>();
        repository.findAllBySymbolIn(symbols).forEach(s -> result.put(s.getSymbol(), new Snapshot(
                new MarketQuote(s.getSymbol(), s.getPrice(), s.getCurrency(), s.getPreviousClose(), s.getMarketTimestamp(),
                        s.getSessionDate(), Boolean.TRUE.equals(s.getConfirmedClose())), s.getFetchedAt())));
        LOGGER.debug("Market snapshots loaded: requested={}, returned={}", symbols.size(), result.size());
        return result;
    }

    /** Atomic upsert avoids duplicates and prevents a slower instance overwriting newer data. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Map<String, Snapshot> snapshots, Instant persistedAt) {
        if (snapshots.isEmpty()) return;
        int[][] writes = jdbc.batchUpdate("""
                insert into market_quote_snapshots
                    (symbol, price, currency, previous_close, market_timestamp, fetched_at, source, created_at, updated_at, session_date, confirmed_close)
                values (?, ?, ?, ?, ?, ?, 'twelve-data', ?, ?, ?, ?)
                on conflict (symbol) do update set price = excluded.price, currency = excluded.currency,
                    previous_close = excluded.previous_close, market_timestamp = excluded.market_timestamp,
                    fetched_at = excluded.fetched_at, source = excluded.source, updated_at = excluded.updated_at,
                    session_date = excluded.session_date, confirmed_close = excluded.confirmed_close
                where (coalesce(excluded.session_date, (excluded.market_timestamp at time zone 'America/New_York')::date, '-infinity'::date),
                    coalesce(excluded.confirmed_close, false), coalesce(excluded.market_timestamp, '-infinity'::timestamptz), excluded.fetched_at)
                    >= (coalesce(market_quote_snapshots.session_date, (market_quote_snapshots.market_timestamp at time zone 'America/New_York')::date, '-infinity'::date),
                    coalesce(market_quote_snapshots.confirmed_close, false), coalesce(market_quote_snapshots.market_timestamp, '-infinity'::timestamptz), market_quote_snapshots.fetched_at)
                """, snapshots.values(), snapshots.size(), (statement, snapshot) -> {
            var quote = snapshot.quote();
            statement.setString(1, quote.symbol()); statement.setBigDecimal(2, quote.currentPrice());
            statement.setString(3, quote.currency()); statement.setBigDecimal(4, quote.previousClose());
            statement.setTimestamp(5, quote.marketTimestamp() == null ? null : Timestamp.from(quote.marketTimestamp()));
            statement.setTimestamp(6, Timestamp.from(snapshot.fetchedAt()));
            statement.setTimestamp(7, Timestamp.from(persistedAt)); statement.setTimestamp(8, Timestamp.from(persistedAt));
            statement.setObject(9, quote.sessionDate()); statement.setBoolean(10, quote.confirmedClose());
        });
        long applied = java.util.Arrays.stream(writes).flatMapToInt(java.util.Arrays::stream).filter(count -> count > 0).count();
        long rejectedOlder = java.util.Arrays.stream(writes).flatMapToInt(java.util.Arrays::stream).filter(count -> count == 0).count();
        LOGGER.debug("Market snapshot persistence: attempted={}, applied={}, skippedOlder={}, completedAt={}",
                snapshots.size(), applied, rejectedOlder, persistedAt);
    }
}
