package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.backend.entities.*;
import com.financetracker.backend.repositories.*;
import com.financetracker.backend.security.JwtService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "recurring.scheduler.enabled=false", "app.security.jwt.secret=financial-insights-test-only-secret-32-bytes"
})
@Import(FinancialInsightsIntegrationTests.FixedClock.class)
class FinancialInsightsIntegrationTests {
    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock insightsTestClock() {
            return Clock.fixed(Instant.parse("2026-10-15T16:00:00Z"), ZoneId.of("America/Toronto"));
        }
    }

    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private TransactionRepository transactions;
    @Autowired private BudgetRepository budgets;
    @Autowired private InvestmentRepository investments;
    @Autowired private RecurringTransactionRepository recurring;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    private User owner;
    private User other;
    private final LocalDate today = LocalDate.of(2026, 10, 15);

    @BeforeEach void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        owner = users.save(User.builder().username("insights_owner_" + suffix)
                .email("insights_owner_" + suffix + "@example.com").password("unused").build());
        other = users.save(User.builder().username("insights_other_" + suffix)
                .email("insights_other_" + suffix + "@example.com").password("unused").build());
        transaction(owner, "1000", TransactionType.INCOME, TransactionCategory.SALARY, today);
        transaction(owner, "860", TransactionType.EXPENSE, TransactionCategory.DINING, today);
        transaction(owner, "1000", TransactionType.INCOME, TransactionCategory.SALARY, today.minusMonths(1));
        transaction(owner, "700", TransactionType.EXPENSE, TransactionCategory.DINING, today.minusMonths(1));
        budgets.save(Budget.builder().user(owner).category(TransactionCategory.DINING)
                .monthlyLimit(new BigDecimal("1000")).month(today.withDayOfMonth(1)).build());
        investments.save(Investment.builder().user(owner).ticker("ABC").shares(new BigDecimal("2"))
                .purchasePrice(new BigDecimal("50")).purchaseDate(today).build());
        schedule(owner, "Owner rent", "850", true, TransactionType.EXPENSE);
        schedule(owner, "Paused rent", "9999", false, TransactionType.EXPENSE);
        schedule(owner, "Owner salary", "2000", true, TransactionType.INCOME);
        transaction(other, "99999", TransactionType.EXPENSE, TransactionCategory.DINING, today);
        budgets.save(Budget.builder().user(other).category(TransactionCategory.DINING)
                .monthlyLimit(new BigDecimal("1")).month(today.withDayOfMonth(1)).build());
        investments.save(Investment.builder().user(other).ticker("OTHER").shares(new BigDecimal("999"))
                .purchasePrice(new BigDecimal("999")).purchaseDate(today).build());
        schedule(other, "Other secret rent", "99999", true, TransactionType.EXPENSE);
    }

    @AfterEach void cleanUp() {
        // Delete only the unique test users' fixtures, never existing application records.
        for (User user : new User[]{owner, other}) {
            if (user == null) continue;
            for (String table : List.of("transactions", "budgets", "investments", "recurring_transactions")) {
                jdbc.update("delete from " + table + " where user_id = ?", user.getId());
            }
            users.deleteById(user.getId());
        }
    }

    @Test void realJwtEndpointProducesReasonableInsightsAndPreservesEveryFinancialField() throws Exception {
        var before = snapshot();
        var response = get(owner, "");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.get("insights").size()).isLessThanOrEqualTo(8);
        assertThat(response.body()).contains("86%", "$850.00", "$1150.00", "decreased from 30%", "14%");
        assertThat(response.body()).doesNotContain("99999", "9999", "Other secret", "Paused rent");
        assertThat(snapshot()).isEqualTo(before);
        System.out.println("Financial insights HTTP verification: " + response.body());
    }

    @Test void endpointRejectsAnonymousAndInvalidJwt() throws Exception {
        assertThat(get(null, "").statusCode()).isEqualTo(403);
        var request = HttpRequest.newBuilder(URI.create(url("")))
                .header("Authorization", "Bearer invalid-token").GET().build();
        assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(403);
    }

    @Test void callerCannotChooseAnotherUsersData() throws Exception {
        var response = get(owner, "?userId=" + other.getId());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("$850.00").doesNotContain("99999", "Other secret");
        var otherResponse = get(other, "");
        assertThat(otherResponse.statusCode()).isEqualTo(200);
        assertThat(otherResponse.body()).contains("Other secret rent").doesNotContain("Owner rent", "Owner salary");
    }

    @Test void repeatedRequestsDoNotModifySchedulesOrRecordsAndHaveStableInsights() throws Exception {
        var before = snapshot();
        var first = mapper.readTree(get(owner, "").body());
        var second = mapper.readTree(get(owner, "").body());
        assertThat(first).isEqualTo(second);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select active from recurring_transactions where user_id = ? and description = 'Paused rent'",
                Boolean.class, owner.getId())).isFalse();
    }

    @Test void existingAuthenticatedReadEndpointsAndTransactionFilteringStillWork() throws Exception {
        var before = snapshot();
        for (String path : List.of("/api/dashboard", "/api/analytics", "/api/budgets?month=2026-10",
                "/api/investments", "/api/investments/holdings", "/api/transactions",
                "/api/recurring-transactions", "/api/recurring-transactions/upcoming",
                "/api/recurring-transactions/forecast")) {
            assertThat(getPath(owner, path).statusCode()).as(path).isEqualTo(200);
        }
        var filtered = getPath(owner, "/api/transactions?startDate=2026-10-01&endDate=2026-10-31"
                + "&category=DINING&minAmount=800&maxAmount=900");
        assertThat(filtered.statusCode()).isEqualTo(200);
        JsonNode records = mapper.readTree(filtered.body());
        assertThat(records.size()).isEqualTo(1);
        assertThat(records.get(0).get("amount").decimalValue()).isEqualByComparingTo("860");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void selectedYearHttpReportUsesAnnualTotalsAndRemainsReadOnlyAndOwned() throws Exception {
        var before = snapshot();
        var response = get(owner, "?period=THIS_YEAR&userId=" + other.getId());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = mapper.readTree(response.body());
        var savings = java.util.stream.StreamSupport.stream(body.path("insights").spliterator(), false)
                .filter(i -> i.path("key").asText().equals("savings-rate")).findFirst().orElseThrow();
        assertThat(savings.path("metricValue").decimalValue()).isEqualByComparingTo("22");
        assertThat(savings.path("message").asText()).contains("$440.00", "2026-01-01 through 2026-10-15");
        assertThat(response.body()).doesNotContain("99999", "Other secret", "next 30 days");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void customHttpReportUsesSelectedDatesAndRejectsInvalidRanges() throws Exception {
        var response = get(owner, "?period=CUSTOM&startDate=2026-09-01&endDate=2026-09-30");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("30%", "$300.00", "2026-09-01 through 2026-09-30")
                .doesNotContain("Owner rent", "next 30 days", "this month to date");
        assertThat(get(owner, "?period=CUSTOM").statusCode()).isEqualTo(400);
        assertThat(get(owner, "?period=CUSTOM&startDate=2026-10-10&endDate=2026-10-01").statusCode()).isEqualTo(400);
        assertThat(get(owner, "?period=THIS_YEAR&startDate=2026-01-01").statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(User user, String query) throws Exception {
        return getPath(user, "/api/financial-insights" + query);
    }
    private HttpResponse<String> getPath(User user, String path) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (user != null) builder.header("Authorization", "Bearer " + jwt.generateToken(user));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String url(String query) { return "http://localhost:" + port + "/api/financial-insights" + query; }
    private Map<String, List<Map<String, Object>>> snapshot() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("transactions", "budgets", "investments", "recurring_transactions")) {
            result.put(table, jdbc.queryForList("select * from " + table + " where user_id in (?, ?) order by id",
                    owner.getId(), other.getId()));
        }
        return result;
    }
    private void transaction(User user, String amount, TransactionType type, TransactionCategory category, LocalDate date) {
        transactions.save(Transaction.builder().user(user).amount(new BigDecimal(amount)).type(type)
                .category(category).description("Insights test").date(date).build());
    }
    private void schedule(User user, String description, String amount, boolean active, TransactionType type) {
        recurring.save(RecurringTransaction.builder().user(user).amount(new BigDecimal(amount)).type(type)
                .category(type == TransactionType.EXPENSE ? TransactionCategory.HOUSING : TransactionCategory.SALARY)
                .description(description).frequency(RecurringFrequency.MONTHLY).startDate(today.minusMonths(1).plusDays(4))
                .lastGeneratedDate(today.minusMonths(1).plusDays(4)).nextOccurrence(today.plusDays(4)).active(active).build());
    }
}
