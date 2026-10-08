package com.financetracker.backend.services.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.financetracker.backend.config.*;
import com.financetracker.backend.entities.*;
import com.financetracker.backend.repositories.*;
import com.financetracker.backend.security.JwtService;
import com.financetracker.backend.services.ai.InsightSummaryClient;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** Real PostgreSQL/JWT/HTTP, synthetic isolated symbols; never consumes external API credits. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "recurring.scheduler.enabled=false", "market-data.enabled=true", "market-data.credits-per-minute=500",
        "app.security.jwt.secret=snapshot-integration-test-only-32-bytes", "ai.insights.enabled=false"
})
@Import(MarketQuoteSnapshotIntegrationTests.TestClock.class)
class MarketQuoteSnapshotIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private MarketQuoteService quotes;
    @Autowired private MarketQuoteSnapshotStore store;
    @Autowired private MarketQuoteSnapshotRepository snapshots;
    @Autowired private MarketDataProperties properties;
    @Autowired private InsightsAiProperties aiProperties;
    @Autowired private UserRepository users;
    @Autowired private InvestmentRepository investments;
    @Autowired private TransactionRepository transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private MutableClock clock;
    @MockitoBean private MarketDataProvider provider;
    @MockitoBean private InsightSummaryClient summaryClient;
    private User owner, other;
    private String a, b, c;

    @BeforeEach void setup() {
        clock.now = Instant.parse("2026-10-09T16:00:00Z");
        clearMemory();
        properties.setEnabled(true); properties.setPersistedMaxAgeDays(7);
        properties.setCreditsPerDay(800); properties.setDailyReserve(50);
        aiProperties.setEnabled(false);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        a = "A" + suffix; b = "B" + suffix; c = "C" + suffix;
        owner = user("snapshot_owner_" + suffix); other = user("snapshot_other_" + suffix);
        lot(owner, a, "5", "170"); lot(owner, a, "5", "195"); lot(owner, b, "1", "450");
        lot(other, c, "100", "999");
        when(provider.isConfigured()).thenReturn(true);
        when(provider.getQuotes(anySet())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Set<String> requested = call.getArgument(0);
            Map<String, MarketDataProvider.Result> result = new LinkedHashMap<>();
            requested.forEach(s -> result.put(s, new MarketDataProvider.Result(quote(s, s.equals(a) ? "207.30" : "400"), MarketDataProvider.Status.AVAILABLE)));
            return result;
        });
    }
    @AfterEach void cleanup() {
        snapshots.deleteAllById(List.of(a, b, c));
        for (User u : new User[]{owner, other}) {
            if (u == null) continue;
            jdbc.update("delete from investments where user_id=?", u.getId());
            jdbc.update("delete from transactions where user_id=?", u.getId());
            users.deleteById(u.getId());
        }
        aiProperties.setEnabled(false);
    }

    @Test void successfulProviderBatchPersistsOneQuoteForDuplicateLots() throws Exception {
        assertThat(call(owner, "/api/investments/portfolio").statusCode()).isEqualTo(200);
        assertThat(snapshots.findAllBySymbolIn(List.of(a, b))).hasSize(2);
        var saved = snapshots.findById(a).orElseThrow();
        assertThat(saved.getPrice()).isEqualByComparingTo("207.30");
        assertThat(saved.getPreviousClose()).isEqualByComparingTo("200");
        assertThat(saved.getSource()).isEqualTo("twelve-data");
        assertThat(saved.getFetchedAt()).isEqualTo(clock.instant());
        assertThat(saved.getMarketTimestamp()).isEqualTo(clock.instant().minusSeconds(30));
        verify(provider).getQuotes(Set.of(a, b));
    }
    @Test void secondProviderQuoteUpdatesSamePrimaryKeyAndPreservesCreatedAt() {
        quotes.getQuotes(Set.of(a));
        var created = snapshots.findById(a).orElseThrow().getCreatedAt();
        clock.advance(600);
        when(provider.getQuotes(anySet())).thenReturn(Map.of(a, new MarketDataProvider.Result(quote(a, "210.25"), MarketDataProvider.Status.AVAILABLE)));
        quotes.getQuotes(Set.of(a));
        var updated = snapshots.findById(a).orElseThrow();
        assertThat(updated.getPrice()).isEqualByComparingTo("210.25");
        assertThat(updated.getCreatedAt()).isEqualTo(created);
        assertThat(updated.getUpdatedAt()).isEqualTo(clock.instant());
        assertThat(jdbc.queryForObject("select count(*) from market_quote_snapshots where symbol=?", Long.class, a)).isEqualTo(1);
    }
    @Test void primaryKeyRejectsDuplicateSymbolAndOlderUpsertCannotOverwriteNewerPrice() {
        quotes.getQuotes(Set.of(a));
        assertThatThrownBy(() -> jdbc.update("insert into market_quote_snapshots select * from market_quote_snapshots where symbol=?", a))
                .isInstanceOf(DataIntegrityViolationException.class);
        store.save(Map.of(a, new MarketQuoteSnapshotStore.Snapshot(quote(a, "1"), clock.instant().minusSeconds(60))), clock.instant());
        assertThat(snapshots.findById(a).orElseThrow().getPrice()).isEqualByComparingTo("207.30");
    }
    @Test void atomicConcurrentUpsertsProduceOnlyOneSnapshot() throws Exception {
        var saved = Map.of(a, new MarketQuoteSnapshotStore.Snapshot(quote(a, "210.25"), clock.instant()));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> store.save(saved, clock.instant()));
            var second = executor.submit(() -> store.save(saved, clock.instant()));
            first.get(); second.get();
        }
        assertThat(jdbc.queryForObject("select count(*) from market_quote_snapshots where symbol=?", Long.class, a)).isEqualTo(1);
    }
    @Test void normalizationPersistsOneUppercaseSymbol() {
        quotes.getQuotes(Set.of(a, " " + a.toLowerCase(Locale.ROOT) + " "));
        assertThat(snapshots.findAllBySymbolIn(List.of(a))).hasSize(1);
        verify(provider).getQuotes(Set.of(a));
    }
    @Test void newlyConstructedResolverRecoversFromPostgresDuringWeekendWithZeroProviderCalls() {
        var original = quotes.getQuotes(Set.of(a)).get(a);
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        clearInvocations(provider);
        var restarted = new MarketQuoteService(provider, properties, clock, store);
        var restored = restarted.getQuotes(Set.of(a)).get(a);
        assertThat(restored.quote().currentPrice()).isEqualByComparingTo(original.quote().currentPrice());
        assertThat(restored.fetchedAt()).isEqualTo(original.fetchedAt());
        assertThat(restored.status()).isEqualTo(MarketDataProvider.Status.STALE);
        restarted.refreshQuotes(Set.of(a)); restarted.getQuotes(Set.of(a));
        verifyNoInteractions(provider);
    }
    @Test void restartClosedPortfolioDashboardAndAnalyticsUseSamePersistentValuationAndOwnership() throws Exception {
        var before = financialRows();
        var first = mapper.readTree(call(owner, "/api/investments/portfolio").body());
        clearMemory(); clearInvocations(provider);
        clock.now = Instant.parse("2026-10-09T22:00:00Z");
        var restored = mapper.readTree(call(owner, "/api/investments/portfolio?userId=" + other.getId()).body());
        assertThat(restored.path("summary").path("totalMarketValue").decimalValue()).isEqualByComparingTo("2473");
        assertThat(restored.path("summary").path("totalGainLoss").decimalValue()).isEqualByComparingTo("198");
        assertThat(restored.path("summary").path("totalReturnPercentage").decimalValue()).isEqualByComparingTo("8.70");
        assertThat(restored.path("summary").path("lastUpdated")).isEqualTo(first.path("summary").path("lastUpdated"));
        assertThat(restored.path("summary").path("status").asText()).isEqualTo("STALE");
        assertThat(restored.path("holdings").get(0).path("allocationPercentage").decimalValue()).isEqualByComparingTo("83.83");
        assertThat(mapper.readTree(call(owner, "/api/dashboard").body()).path("summary").path("investmentValue").decimalValue()).isEqualByComparingTo("2473");
        assertThat(mapper.readTree(call(owner, "/api/analytics").body()).path("portfolio").path("summary").path("totalMarketValue").decimalValue()).isEqualByComparingTo("2473");
        assertThat(call(owner, "/api/financial-insights").body()).doesNotContain("investment-performance", c);
        assertThat(financialRows()).isEqualTo(before);
        verifyNoInteractions(provider);
        assertThat(call(null, "/api/investments/portfolio").statusCode()).isEqualTo(403);
        assertThat(call(other, "/api/investments/portfolio?quotePolicy=CACHE_ONLY").body()).doesNotContain(a, b);
        verifyNoInteractions(provider);
    }
    @Test void closedColdPortfolioFetchesOnceAndSurvivesAnotherMemoryReset() throws Exception {
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        assertThat(mapper.readTree(call(owner, "/api/investments/portfolio").body()).path("summary").path("status").asText()).isEqualTo("STALE");
        clearMemory();
        for (int attempt = 0; attempt < 3; attempt++) call(owner, "/api/investments/portfolio");
        verify(provider, times(1)).getQuotes(Set.of(a, b));
        assertThat(snapshots.findAllBySymbolIn(List.of(a, b))).hasSize(2);
    }
    @Test void nextOpenRefreshesPersistedPriorSessionDataAndUpdatesPostgres() {
        quotes.getQuotes(Set.of(a)); clearMemory();
        clock.now = Instant.parse("2026-10-11T16:00:00Z");
        quotes.getQuotes(Set.of(a));
        clock.now = Instant.parse("2026-10-12T13:35:00Z");
        assertThat(quotes.getQuotes(Set.of(a)).get(a).status()).isEqualTo(MarketDataProvider.Status.AVAILABLE);
        assertThat(snapshots.findById(a).orElseThrow().getFetchedAt()).isEqualTo(clock.instant());
        verify(provider, times(2)).getQuotes(Set.of(a));
    }
    @Test void providerFailureRecoversPersistentQuoteWithOriginalTime() {
        quotes.getQuotes(Set.of(a)); clearMemory(); clock.advance(600);
        when(provider.getQuotes(anySet())).thenThrow(new IllegalStateException("not displayed"));
        var result = quotes.getQuotes(Set.of(a)).get(a);
        assertThat(result.status()).isEqualTo(MarketDataProvider.Status.STALE);
        assertThat(result.fetchedAt()).isEqualTo(clock.instant().minusSeconds(600));
        assertThat(snapshots.findById(a).orElseThrow().getFetchedAt()).isEqualTo(result.fetchedAt());
    }
    @Test void tooOldPersistentSnapshotDoesNotBecomeFinancialInsightOrFakeZeroValuation() throws Exception {
        quotes.getQuotes(Set.of(a, b)); clearMemory(); clock.advance(8 * 86400);
        when(provider.getQuotes(anySet())).thenThrow(new IllegalStateException());
        var response = mapper.readTree(call(owner, "/api/investments/portfolio").body());
        assertThat(response.path("summary").path("totalMarketValue").isNull()).isTrue();
        assertThat(call(owner, "/api/financial-insights").body()).doesNotContain("investment-performance");
    }
    @Test void insightsAndGeminiLoadAcceptableSnapshotsButNeverFetchQuotes() throws Exception {
        quotes.getQuotes(Set.of(a, b)); clearMemory(); clock.advance(600);
        clearInvocations(provider);
        transactions.save(Transaction.builder().user(owner).amount(new BigDecimal("1000"))
                .type(TransactionType.INCOME).category(TransactionCategory.SALARY).date(LocalDate.now(clock)).build());
        assertThat(call(owner, "/api/financial-insights").body()).doesNotContain("investment-performance");
        aiProperties.setEnabled(true);
        when(summaryClient.isConfigured()).thenReturn(true);
        when(summaryClient.summarize(anyList())).thenAnswer(call -> {
            List<InsightSummaryClient.SourceInsight> source = call.getArgument(0);
            return String.join(" ", source.stream().map(InsightSummaryClient.SourceInsight::message).toList());
        });
        var response = call(owner, "/api/financial-insights/summary");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("status").asText()).isEqualTo("AVAILABLE");
        verify(summaryClient).summarize(argThat(source -> source.stream().noneMatch(s -> s.message().contains("unrealized"))));
        verifyNoInteractions(provider);
    }

    private MarketQuote quote(String symbol, String price) {
        return new MarketQuote(symbol, new BigDecimal(price), "USD", new BigDecimal("200"), clock.instant().minusSeconds(30));
    }
    private void clearMemory() {
        ((Map<?, ?>) ReflectionTestUtils.getField(quotes, "cache")).clear();
        for (String field : List.of("minute", "day", "nextManualRefreshAt", "backoffUntil")) ReflectionTestUtils.setField(quotes, field, null);
        for (String field : List.of("minuteCredits", "dayCredits", "minuteRequests", "dayRequests")) ReflectionTestUtils.setField(quotes, field, 0);
    }
    private User user(String name) { return users.save(User.builder().username(name).email(name.toLowerCase(Locale.ROOT) + "@example.invalid").password("unused").build()); }
    private void lot(User user, String symbol, String shares, String price) {
        investments.save(Investment.builder().user(user).ticker(symbol).shares(new BigDecimal(shares)).purchasePrice(new BigDecimal(price)).purchaseDate(LocalDate.of(2026, 9, 1)).build());
    }
    private List<Map<String, Object>> financialRows() {
        return jdbc.queryForList("select * from investments where user_id in (?, ?) order by id", owner.getId(), other.getId());
    }
    private HttpResponse<String> call(User user, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (user != null) request.header("Authorization", "Bearer " + jwt.generateToken(user));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    @TestConfiguration static class TestClock { @Bean @Primary MutableClock snapshotTestClock() { return new MutableClock(); } }
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-09T16:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
