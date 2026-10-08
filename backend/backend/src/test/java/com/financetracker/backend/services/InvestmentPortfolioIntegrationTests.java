package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.financetracker.backend.config.MarketDataProperties;
import com.financetracker.backend.config.InsightsAiProperties;
import com.financetracker.backend.entities.*;
import com.financetracker.backend.repositories.*;
import com.financetracker.backend.security.JwtService;
import com.financetracker.backend.services.market.*;
import com.financetracker.backend.services.ai.InsightSummaryClient;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "recurring.scheduler.enabled=false", "market-data.enabled=true", "market-data.credits-per-minute=500",
        "app.security.jwt.secret=portfolio-integration-test-only-32-bytes"
})
@Import(InvestmentPortfolioIntegrationTests.TestClock.class)
class InvestmentPortfolioIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private InvestmentRepository investments;
    @Autowired private TransactionRepository transactions;
    @Autowired private InsightsAiProperties aiProperties;
    @Autowired private MutableClock clock;
    @Autowired private MarketDataProperties properties;
    @Autowired private MarketQuoteService quoteService;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private MarketDataProvider provider;
    @MockitoBean private MarketQuoteSnapshotStore snapshots;
    @MockitoBean private InsightSummaryClient summaryClient;
    private User owner, other;
    @BeforeEach void setup() {
        ((Map<?, ?>) ReflectionTestUtils.getField(quoteService, "cache")).clear();
        for (String field : List.of("minute", "day", "nextManualRefreshAt", "backoffUntil")) ReflectionTestUtils.setField(quoteService, field, null);
        for (String field : List.of("minuteCredits", "dayCredits", "minuteRequests", "dayRequests")) ReflectionTestUtils.setField(quoteService, field, 0);
        clock.now = Instant.parse("2026-10-15T16:00:00Z");
        aiProperties.setEnabled(false);
        properties.setCreditsPerDay(800); properties.setDailyReserve(50);
        properties.setMaxRequestsPerMinute(500);
        properties.setEnabled(true);
        String suffix = UUID.randomUUID().toString().substring(0, 10);
        owner = user("portfolio_owner_" + suffix); other = user("portfolio_other_" + suffix);
        lot(owner, "AAPL", "5", "170"); lot(owner, "AAPL", "5", "195"); lot(owner, "MSFT", "1", "450");
        lot(other, "OTHER", "100", "999");
        when(provider.isConfigured()).thenReturn(true);
        when(provider.getQuotes(anySet())).thenAnswer(call -> {
            // No DB transaction is held while quote HTTP would be running.
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Set<String> symbols = call.getArgument(0);
            Map<String, MarketDataProvider.Result> prices = new LinkedHashMap<>();
            symbols.forEach(s -> prices.put(s, new MarketDataProvider.Result(new MarketQuote(s,
                    new BigDecimal(s.equals("AAPL") ? "207.3" : "400"), "USD", null, Instant.parse("2026-10-15T16:00:00Z")), MarketDataProvider.Status.AVAILABLE)));
            return prices;
        });
    }
    @AfterEach void cleanup() {
        properties.setEnabled(true);
        for (User user : new User[]{owner, other}) {
            if (user == null) continue;
            jdbc.update("delete from investments where user_id = ?", user.getId());
            jdbc.update("delete from transactions where user_id = ?", user.getId());
            users.deleteById(user.getId());
        }
        aiProperties.setEnabled(false);
    }
    @Test void aggregatesLotsAndValuesPortfolioWithoutMutatingAnyPurchaseField() throws Exception {
        var before = snapshot();
        var response = request(owner, "/api/investments/portfolio?userId=" + other.getId(), "GET", null);
        assertThat(response.statusCode()).isEqualTo(200);
        var body = mapper.readTree(response.body());
        assertThat(body.path("summary").path("totalCostBasis").decimalValue()).isEqualByComparingTo("2275");
        assertThat(body.path("summary").path("totalMarketValue").decimalValue()).isEqualByComparingTo("2473");
        assertThat(body.path("summary").path("totalGainLoss").decimalValue()).isEqualByComparingTo("198");
        assertThat(body.path("summary").path("totalReturnPercentage").decimalValue()).isEqualByComparingTo("8.70");
        assertThat(body.path("holdings").get(0).path("averagePurchasePrice").decimalValue()).isEqualByComparingTo("182.5");
        assertThat(body.path("holdings").get(0).path("totalShares").decimalValue()).isEqualByComparingTo("10");
        assertThat(response.body()).doesNotContain("OTHER", "999", "apiKey");
        assertThat(snapshot()).isEqualTo(before);
        request(owner, "/api/investments/portfolio", "GET", null);
        verify(provider, times(1)).getQuotes(Set.of("AAPL", "MSFT"));
    }
    @Test void closedMarketPortfolioRefreshAndOtherPagesReuseSnapshotWithoutProviderCallsOrWrites() throws Exception {
        var before = snapshot();
        var first = mapper.readTree(request(owner, "/api/investments/portfolio", "GET", null).body());
        clock.now = Instant.parse("2026-10-15T22:00:00Z");
        var closed = mapper.readTree(request(owner, "/api/investments/portfolio", "GET", null).body());
        assertThat(closed.path("summary").path("totalMarketValue")).isEqualTo(first.path("summary").path("totalMarketValue"));
        assertThat(closed.path("summary").path("lastUpdated")).isEqualTo(first.path("summary").path("lastUpdated"));
        assertThat(closed.path("summary").path("status").asText()).isEqualTo("STALE");
        assertThat(request(owner, "/api/investments/portfolio/refresh", "POST", null).statusCode()).isEqualTo(200);
        assertThat(request(owner, "/api/dashboard", "GET", null).statusCode()).isEqualTo(200);
        assertThat(request(owner, "/api/analytics", "GET", null).statusCode()).isEqualTo(200);
        var coldOther = mapper.readTree(request(other, "/api/investments/portfolio", "GET", null).body());
        assertThat(coldOther.path("summary").path("status").asText()).isEqualTo("STALE");
        assertThat(coldOther.toString()).doesNotContain("AAPL", "MSFT");
        verify(provider, times(1)).getQuotes(Set.of("AAPL", "MSFT"));
        verify(provider, times(1)).getQuotes(Set.of("OTHER"));
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void jwtAuthenticationAndUserIsolationAreEnforced() throws Exception {
        assertThat(request(null, "/api/investments/portfolio", "GET", null).statusCode()).isEqualTo(403);
        var otherResponse = request(other, "/api/investments/portfolio", "GET", null);
        assertThat(otherResponse.statusCode()).isEqualTo(200);
        assertThat(otherResponse.body()).contains("OTHER").doesNotContain("AAPL", "MSFT");
    }
    @Test void dashboardAnalyticsAndInsightsReuseTheSamePortfolioAndQuotes() throws Exception {
        var dashboard = mapper.readTree(request(owner, "/api/dashboard", "GET", null).body());
        assertThat(dashboard.path("summary").path("investmentValue").decimalValue()).isEqualByComparingTo("2473");
        var analytics = mapper.readTree(request(owner, "/api/analytics", "GET", null).body());
        assertThat(analytics.path("portfolio").path("summary").path("totalGainLoss").decimalValue()).isEqualByComparingTo("198");
        var insights = request(owner, "/api/financial-insights", "GET", null);
        assertThat(insights.body()).contains("investment-performance", "$2473.00", "unrealized gain");
        verify(provider, times(1)).getQuotes(Set.of("AAPL", "MSFT"));
    }
    @Test void disabledProviderPreservesCostsAndOmitsPerformance() throws Exception {
        properties.setEnabled(false);
        var portfolio = mapper.readTree(request(owner, "/api/investments/portfolio", "GET", null).body());
        assertThat(portfolio.path("summary").path("status").asText()).isEqualTo("DISABLED");
        assertThat(portfolio.path("summary").path("totalMarketValue").isNull()).isTrue();
        assertThat(portfolio.path("summary").path("totalCostBasis").decimalValue()).isEqualByComparingTo("2275");
        assertThat(request(owner, "/api/financial-insights", "GET", null).body()).doesNotContain("investment-performance");
        var dashboard = mapper.readTree(request(owner, "/api/dashboard", "GET", null).body());
        assertThat(dashboard.path("summary").path("investmentValue").decimalValue()).isEqualByComparingTo("2275");
        verifyNoInteractions(provider);
    }
    @Test void failedProviderLeavesPurchaseHistoryAvailableAndTotalsUnknown() throws Exception {
        when(provider.getQuotes(anySet())).thenThrow(new IllegalStateException());
        var result = mapper.readTree(request(owner, "/api/investments/portfolio", "GET", null).body());
        assertThat(result.path("summary").path("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(result.path("summary").path("totalMarketValue").isNull()).isTrue();
        assertThat(request(owner, "/api/investments", "GET", null).statusCode()).isEqualTo(200);
        assertThat(request(owner, "/api/financial-insights", "GET", null).body()).doesNotContain("investment-performance");
    }
    @Test void purchaseCrudAndOwnershipRemainWorking() throws Exception {
        var created = request(owner, "/api/investments", "POST",
                "{\"ticker\":\" aapl \",\"shares\":1,\"purchasePrice\":180,\"purchaseDate\":\"2026-09-01\"}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        var record = mapper.readTree(created.body());
        assertThat(record.path("ticker").asText()).isEqualTo("AAPL");
        long id = record.path("id").asLong();
        assertThat(request(other, "/api/investments/" + id, "DELETE", null).statusCode()).isEqualTo(404);
        assertThat(request(owner, "/api/investments/" + id, "PUT",
                "{\"ticker\":\"MSFT\",\"shares\":2,\"purchasePrice\":200,\"purchaseDate\":\"2026-09-01\"}").statusCode()).isEqualTo(200);
        assertThat(request(owner, "/api/investments/" + id, "DELETE", null).statusCode()).isEqualTo(204);
    }
    @Test void dashboardThenInvestmentsAndBrowserReloadReuseOneBatch() throws Exception {
        for (String path : List.of("/api/dashboard", "/api/investments/portfolio", "/api/analytics", "/api/investments/portfolio")) {
            assertThat(request(owner, path, "GET", null).statusCode()).isEqualTo(200);
        }
        verify(provider, times(1)).getQuotes(Set.of("AAPL", "MSFT"));
    }
    @Test void manualRefreshCannotBeBypassedByRepeatedAuthenticatedRequests() throws Exception {
        var initial = request(owner, "/api/investments/portfolio/refresh", "POST", null);
        assertThat(initial.statusCode()).isEqualTo(200);
        assertThat(initial.body()).contains("ACCEPTED", "2473.00");
        assertThat(initial.headers().firstValue("Retry-After")).contains("120");
        for (int attempt = 0; attempt < 5; attempt++) {
            var blocked = request(owner, "/api/investments/portfolio/refresh", "POST", null);
            assertThat(blocked.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(blocked.body()).path("status").asText()).isEqualTo("COOLDOWN");
        }
        var otherResponse = request(other, "/api/investments/portfolio/refresh?userId=" + owner.getId(), "POST", null);
        assertThat(otherResponse.body()).contains("OTHER").doesNotContain("AAPL", "MSFT");
        assertThat(request(null, "/api/investments/portfolio/refresh", "POST", null).statusCode()).isEqualTo(403);
        clock.advance(120);
        assertThat(request(owner, "/api/investments/portfolio/refresh", "POST", null).body()).contains("ACCEPTED");
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void addingNewTickerOnlyRequestsThatSymbolEvenWithStaleExistingQuotes() throws Exception {
        request(owner, "/api/investments/portfolio", "GET", null);
        clock.advance(601);
        assertThat(request(owner, "/api/investments", "POST", lotJson(" nvda ", "2", "100")).statusCode()).isEqualTo(201);
        var result = request(owner, "/api/investments/portfolio?quotePolicy=MISSING_ONLY", "GET", null);
        assertThat(result.statusCode()).isEqualTo(200);
        verify(provider).getQuotes(Set.of("AAPL", "MSFT"));
        verify(provider).getQuotes(Set.of("NVDA"));
        verify(provider, times(2)).getQuotes(anySet());
    }
    @Test void editingSharesRecalculatesUsingCachedQuotesWithoutRefresh() throws Exception {
        editLotUsingCache("7", "170");
    }
    @Test void editingPurchasePriceRecalculatesUsingCachedQuotesWithoutRefresh() throws Exception {
        editLotUsingCache("5", "200");
    }
    private void editLotUsingCache(String shares, String price) throws Exception {
        request(owner, "/api/investments/portfolio", "GET", null);
        clock.advance(601);
        long id = investments.findByUserIdOrderByPurchaseDateDesc(owner.getId()).stream().filter(i -> i.getTicker().equals("AAPL")).findFirst().orElseThrow().getId();
        assertThat(request(owner, "/api/investments/" + id, "PUT", lotJson("AAPL", shares, price)).statusCode()).isEqualTo(200);
        var result = mapper.readTree(request(owner, "/api/investments/portfolio?quotePolicy=CACHE_ONLY", "GET", null).body());
        assertThat(result.path("summary").path("status").asText()).isEqualTo("STALE");
        assertThat(result.path("summary").path("totalCostBasis").decimalValue()).isNotEqualByComparingTo("2275");
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void deletingLotReusesRemainingCachedQuotesWithoutRefresh() throws Exception {
        request(owner, "/api/investments/portfolio", "GET", null);
        clock.advance(601);
        long id = investments.findByUserIdOrderByPurchaseDateDesc(owner.getId()).stream().filter(i -> i.getTicker().equals("MSFT")).findFirst().orElseThrow().getId();
        assertThat(request(owner, "/api/investments/" + id, "DELETE", null).statusCode()).isEqualTo(204);
        var result = mapper.readTree(request(owner, "/api/investments/portfolio?quotePolicy=CACHE_ONLY", "GET", null).body());
        assertThat(result.path("summary").path("totalMarketValue").decimalValue()).isEqualByComparingTo("2073");
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void insightsNeverFetchQuotesForColdOrExpiredCache() throws Exception {
        assertThat(request(owner, "/api/financial-insights", "GET", null).body()).doesNotContain("investment-performance");
        verify(provider, never()).getQuotes(anySet());
        request(owner, "/api/investments/portfolio", "GET", null);
        assertThat(request(owner, "/api/financial-insights", "GET", null).body()).contains("investment-performance");
        clock.advance(601);
        assertThat(request(owner, "/api/financial-insights", "GET", null).body()).doesNotContain("investment-performance");
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void geminiSummarizesCanonicalInsightsWithoutFetchingMarketData() throws Exception {
        aiProperties.setEnabled(true);
        transactions.save(Transaction.builder().user(owner).amount(new BigDecimal("1000"))
                .type(TransactionType.INCOME).category(TransactionCategory.SALARY).date(LocalDate.of(2026, 10, 15)).build());
        when(summaryClient.isConfigured()).thenReturn(true);
        when(summaryClient.summarize(anyList())).thenAnswer(call -> {
            List<InsightSummaryClient.SourceInsight> source = call.getArgument(0);
            return String.join(" ", source.stream().map(InsightSummaryClient.SourceInsight::message).toList());
        });
        var cold = request(owner, "/api/financial-insights/summary", "GET", null);
        assertThat(mapper.readTree(cold.body()).path("status").asText()).isEqualTo("AVAILABLE");
        verify(provider, never()).getQuotes(anySet());
        request(owner, "/api/investments/portfolio", "GET", null);
        clock.advance(601);
        assertThat(mapper.readTree(request(owner, "/api/financial-insights/summary", "GET", null).body()).path("status").asText()).isEqualTo("AVAILABLE");
        verify(summaryClient, times(2)).summarize(anyList());
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void emptyHoldingsNeverRequestMarketDataOnAnyPage() throws Exception {
        jdbc.update("delete from investments where user_id = ?", owner.getId());
        for (String path : List.of("/api/investments/portfolio", "/api/dashboard", "/api/analytics", "/api/financial-insights")) {
            assertThat(request(owner, path, "GET", null).statusCode()).isEqualTo(200);
        }
        verifyNoInteractions(provider);
    }
    @Test void quotaExhaustionKeepsApplicationUsableWithClearlyStaleValues() throws Exception {
        properties.setCreditsPerDay(2); properties.setDailyReserve(0);
        request(owner, "/api/investments/portfolio", "GET", null);
        clock.advance(601);
        var result = mapper.readTree(request(owner, "/api/dashboard", "GET", null).body());
        assertThat(result.path("portfolio").path("summary").path("status").asText()).isEqualTo("STALE");
        assertThat(result.path("portfolio").path("summary").path("totalMarketValue").decimalValue()).isEqualByComparingTo("2473");
        assertThat(request(owner, "/api/analytics", "GET", null).statusCode()).isEqualTo(200);
        verify(provider, times(1)).getQuotes(anySet());
    }
    @Test void concurrentPortfolioHttpRequestsReuseOneBatch() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> request(owner, "/api/investments/portfolio", "GET", null));
            var second = executor.submit(() -> request(owner, "/api/investments/portfolio", "GET", null));
            assertThat(first.get().statusCode()).isEqualTo(200);
            assertThat(second.get().statusCode()).isEqualTo(200);
        }
        verify(provider, times(1)).getQuotes(Set.of("AAPL", "MSFT"));
    }
    private String lotJson(String ticker, String shares, String price) {
        return "{\"ticker\":\"" + ticker + "\",\"shares\":" + shares + ",\"purchasePrice\":" + price + ",\"purchaseDate\":\"2026-09-01\"}";
    }
    @TestConfiguration static class TestClock {
        @Bean @Primary MutableClock portfolioTestClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-15T16:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    private User user(String name) { return users.save(User.builder().username(name).email(name + "@example.com").password("unused").build()); }
    private void lot(User user, String ticker, String shares, String price) {
        investments.save(Investment.builder().user(user).ticker(ticker).shares(new BigDecimal(shares)).purchasePrice(new BigDecimal(price)).purchaseDate(LocalDate.of(2026, 9, 1)).build());
    }
    private List<Map<String, Object>> snapshot() { return jdbc.queryForList("select * from investments where user_id in (?, ?) order by id", owner.getId(), other.getId()); }
    private HttpResponse<String> request(User user, String path, String method, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) builder.header("Authorization", "Bearer " + jwt.generateToken(user));
        builder.header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
