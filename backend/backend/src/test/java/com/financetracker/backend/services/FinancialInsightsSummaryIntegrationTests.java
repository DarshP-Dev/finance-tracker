package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.financetracker.backend.config.InsightsAiProperties;
import com.financetracker.backend.entities.*;
import com.financetracker.backend.repositories.*;
import com.financetracker.backend.security.JwtService;
import com.financetracker.backend.services.ai.InsightSummaryClient;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "recurring.scheduler.enabled=false", "ai.insights.enabled=true",
        "app.security.jwt.secret=financial-summary-test-only-secret-32-bytes"
})
@Import(FinancialInsightsIntegrationTests.FixedClock.class)
class FinancialInsightsSummaryIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private TransactionRepository transactions;
    @Autowired private BudgetRepository budgets;
    @Autowired private RecurringTransactionRepository recurring;
    @Autowired private InvestmentRepository investments;
    @Autowired private InsightsAiProperties properties;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private InsightSummaryClient provider;
    private User owner, other;
    private final LocalDate today = LocalDate.of(2026, 10, 15);

    @BeforeEach void setUp() {
        properties.setEnabled(true);
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        owner = users.save(User.builder().username("summary_owner_" + suffix).email("summary_owner_" + suffix + "@example.com").password("unused").build());
        other = users.save(User.builder().username("summary_other_" + suffix).email("summary_other_" + suffix + "@example.com").password("unused").build());
        transaction(owner, "1000", TransactionType.INCOME, TransactionCategory.SALARY);
        transaction(owner, "860", TransactionType.EXPENSE, TransactionCategory.DINING);
        budgets.save(Budget.builder().user(owner).category(TransactionCategory.DINING).monthlyLimit(new BigDecimal("1000")).month(today.withDayOfMonth(1)).build());
        recurring.save(RecurringTransaction.builder().user(owner).amount(new BigDecimal("850"))
                .type(TransactionType.EXPENSE).category(TransactionCategory.HOUSING).description("Private tenant address")
                .frequency(RecurringFrequency.MONTHLY).startDate(today.minusMonths(1).plusDays(4))
                .lastGeneratedDate(today.minusMonths(1).plusDays(4)).nextOccurrence(today.plusDays(4)).active(true).build());
        recurring.save(RecurringTransaction.builder().user(owner).amount(new BigDecimal("9999"))
                .type(TransactionType.EXPENSE).category(TransactionCategory.HOUSING).description("Paused private schedule")
                .frequency(RecurringFrequency.MONTHLY).startDate(today.minusMonths(1)).nextOccurrence(today).active(false).build());
        investments.save(Investment.builder().user(owner).ticker("ABC").shares(new BigDecimal("2"))
                .purchasePrice(new BigDecimal("50")).purchaseDate(today).build());
        transaction(other, "99999", TransactionType.EXPENSE, TransactionCategory.DINING);
        when(provider.isConfigured()).thenReturn(true);
        when(provider.summarize(anyList())).thenAnswer(call -> {
            List<InsightSummaryClient.SourceInsight> source = call.getArgument(0);
            return source.getFirst().message() + " " + source.get(1).message();
        });
    }
    @AfterEach void cleanUp() {
        properties.setEnabled(true);
        for (User user : new User[]{owner, other}) {
            if (user == null) continue;
            for (String table : List.of("transactions", "budgets", "investments", "recurring_transactions")) jdbc.update("delete from " + table + " where user_id=?", user.getId());
            users.deleteById(user.getId());
        }
    }
    @Test void realJwtSummaryIsReadOnlyAndMinimal() throws Exception {
        var before = snapshot();
        var response = get(owner, "/api/financial-insights/summary");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(snapshot()).isEqualTo(before);
        verify(provider).summarize(argThat(source -> {
            String payload = mapper.writeValueAsString(source);
            return !payload.contains("Private tenant") && !payload.contains("Paused private") && !payload.contains("9999")
                    && !payload.contains(owner.getEmail()) && !payload.contains("userId") && !payload.contains("description");
        }));
    }
    @Test void summaryRejectsAnonymousAndInvalidJwt() throws Exception {
        assertThat(get(null, "/api/financial-insights/summary").statusCode()).isEqualTo(403);
        var request = HttpRequest.newBuilder(URI.create(url("/api/financial-insights/summary"))).header("Authorization", "Bearer invalid-token").GET().build();
        assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        verifyNoInteractions(provider);
    }
    @Test void ownershipAndCacheNeverUseRequestedUserId() throws Exception {
        var response = get(owner, "/api/financial-insights/summary?userId=" + other.getId());
        assertThat(response.body()).contains("86%").doesNotContain("99999");
        var theirs = get(other, "/api/financial-insights/summary");
        assertThat(theirs.body()).contains("DETERMINISTIC", "$99999.00").doesNotContain("86%");
        assertThat(get(owner, "/api/financial-insights/summary").body()).isEqualTo(response.body());
        verify(provider, times(1)).summarize(anyList());
    }
    @Test void providerFailureKeepsCanonicalEndpointAndRecordsAvailable() throws Exception {
        doThrow(new InsightSummaryClient.ProviderUnavailableException()).when(provider).summarize(anyList());
        var before = snapshot();
        var response = get(owner, "/api/financial-insights/summary");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UNAVAILABLE").doesNotContain("apiKey", "Private tenant");
        assertThat(get(owner, "/api/financial-insights").body()).contains("86%", "Private tenant address");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void disabledSummaryDoesNotAffectDeterministicInsights() throws Exception {
        properties.setEnabled(false);
        assertThat(get(owner, "/api/financial-insights/summary").body()).contains("DISABLED");
        assertThat(get(owner, "/api/financial-insights").statusCode()).isEqualTo(200);
        verifyNoInteractions(provider);
    }
    @Test void newUserHasEmptySummaryWithoutProviderRequest() throws Exception {
        jdbc.update("delete from transactions where user_id=?", other.getId());
        assertThat(get(other, "/api/financial-insights/summary").body()).contains("EMPTY");
        verifyNoInteractions(provider);
    }
    @Test void repeatedSummaryRequestsUseCacheWithoutChangingFinancialRecords() throws Exception {
        var before = snapshot();
        var first = get(owner, "/api/financial-insights/summary");
        assertThat(get(owner, "/api/financial-insights/summary").body()).isEqualTo(first.body());
        verify(provider, times(1)).summarize(anyList());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void selectedPeriodSummaryMatchesCanonicalAnnualInsightsAndIsReadOnly() throws Exception {
        var before = snapshot();
        var response = get(owner, "/api/financial-insights/summary?period=THIS_YEAR&userId=" + other.getId());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = mapper.readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo("AVAILABLE");
        var canonical = mapper.readTree(get(owner, "/api/financial-insights?period=THIS_YEAR").body());
        String expected = canonical.path("insights").get(0).path("message").asText()
                + " " + canonical.path("insights").get(1).path("message").asText();
        assertThat(body.path("summary").asText()).isEqualTo(expected);
        assertThat(response.body()).doesNotContain("99999", "next 30 days");
        assertThat(snapshot()).isEqualTo(before);
        assertThat(get(owner, "/api/financial-insights/summary?period=CUSTOM").statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(User user, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url(path))).GET();
        if (user != null) request.header("Authorization", "Bearer " + jwt.generateToken(user));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String url(String path) { return "http://localhost:" + port + path; }
    private Map<String, List<Map<String, Object>>> snapshot() {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("transactions", "budgets", "investments", "recurring_transactions")) snapshot.put(table,
                jdbc.queryForList("select * from " + table + " where user_id in (?, ?) order by id", owner.getId(), other.getId()));
        return snapshot;
    }
    private void transaction(User user, String amount, TransactionType type, TransactionCategory category) {
        transactions.save(Transaction.builder().user(user).amount(new BigDecimal(amount)).type(type).category(category).description("Private raw transaction text").date(today).build());
    }
}
