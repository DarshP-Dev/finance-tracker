package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.financetracker.backend.config.NotificationProperties;
import com.financetracker.backend.dto.TransactionRequest;
import com.financetracker.backend.entities.*;
import com.financetracker.backend.repositories.*;
import com.financetracker.backend.security.JwtService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "recurring.scheduler.enabled=false", "market-data.enabled=false", "ai.insights.enabled=false",
    "app.security.jwt.secret=notification-test-only-secret-32-bytes"
})
@Import(NotificationIntegrationTests.TestClock.class)
class NotificationIntegrationTests {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private BudgetRepository budgets;
    @Autowired private RecurringTransactionRepository recurring;
    @Autowired private TransactionRepository transactions;
    @MockitoSpyBean private NotificationRepository notifications;
    @Autowired private NotificationRulesService rules;
    @Autowired private NotificationProperties properties;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionService transactionService;
    @Autowired private BudgetService budgetService;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private RecurringTransactionService recurringService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private MutableClock clock;
    @MockitoSpyBean private NotificationService notificationService;
    private User owner, other;
    private Budget budget;

    @BeforeEach void setup() {
        clock.now = Instant.parse("2026-10-15T16:00:00Z");
        properties.setBudgetWarningPercent(new BigDecimal("80")); properties.setRecurringUpcomingDays(3);
        String suffix = UUID.randomUUID().toString().substring(0, 10);
        owner = user("notification_owner_" + suffix); other = user("notification_other_" + suffix);
        budget = budgets.save(Budget.builder().user(owner).category(TransactionCategory.DINING)
                .monthlyLimit(new BigDecimal("100")).month(TODAY.withDayOfMonth(1)).build());
    }
    @AfterEach void cleanup() {
        reset(notificationService);
        reset(notifications);
        for (User user : new User[]{owner, other}) {
            if (user == null) continue;
            for (String table : List.of("notifications", "notification_receipts", "transactions", "budgets", "recurring_transactions"))
                jdbc.update("delete from " + table + " where user_id = ?", user.getId());
            users.deleteById(user.getId());
        }
    }

    @Test void newUserHasEmptyListAndZeroUnreadCount() throws Exception {
        assertThat(list(owner).path("notifications").size()).isZero(); assertThat(count(owner)).isZero();
    }
    @Test void notificationIsPersistedForOwnerWithoutExposingUserDetails() throws Exception {
        alert(owner, "one");
        var response = call(owner, "/api/notifications?userId=" + other.getId(), "GET");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Budget warning").doesNotContain(owner.getEmail(), "password", "referenceKey", "\"user\"");
        assertThat(jdbc.queryForObject("select user_id from notifications where id=?", Long.class, own().getFirst().getId())).isEqualTo(owner.getId());
    }
    @Test void listsAndCountsNeverIncludeOtherUsers() throws Exception {
        alert(owner, "own"); alert(other, "other"); alert(other, "other2");
        assertThat(list(owner).path("notifications").size()).isEqualTo(1);
        assertThat(count(owner)).isEqualTo(1); assertThat(count(other)).isEqualTo(2);
    }
    @Test void cannotMarkAnotherUsersNotificationRead() throws Exception {
        alert(other, "other"); long id = notifications.findByUserId(other.getId(), org.springframework.data.domain.PageRequest.of(0, 20)).getContent().getFirst().getId();
        assertThat(call(owner, "/api/notifications/" + id + "/read", "PATCH").statusCode()).isEqualTo(404);
        assertThat(count(other)).isEqualTo(1);
    }
    @Test void cannotDeleteAnotherUsersNotification() throws Exception {
        alert(other, "other"); long id = notifications.findByUserId(other.getId(), org.springframework.data.domain.PageRequest.of(0, 20)).getContent().getFirst().getId();
        assertThat(call(owner, "/api/notifications/" + id, "DELETE").statusCode()).isEqualTo(404);
        assertThat(notifications.existsById(id)).isTrue();
    }
    @Test void everyEndpointRequiresAuthentication() throws Exception {
        for (var path : List.of("/api/notifications", "/api/notifications/unread-count"))
            assertThat(call(null, path, "GET").statusCode()).isEqualTo(403);
        assertThat(call(null, "/api/notifications/1/read", "PATCH").statusCode()).isEqualTo(403);
        assertThat(call(null, "/api/notifications/read-all", "PATCH").statusCode()).isEqualTo(403);
        assertThat(call(null, "/api/notifications/1", "DELETE").statusCode()).isEqualTo(403);
    }
    @Test void markingOneReadIsIdempotentAndUpdatesCount() throws Exception {
        alert(owner, "one"); alert(owner, "two"); long id = own().getFirst().getId();
        assertThat(call(owner, "/api/notifications/" + id + "/read", "PATCH").body()).contains("\"read\":true");
        assertThat(call(owner, "/api/notifications/" + id + "/read", "PATCH").statusCode()).isEqualTo(200);
        assertThat(count(owner)).isEqualTo(1);
    }
    @Test void markAllReadPreservesHistoryAndDoesNotAffectOtherUser() throws Exception {
        alert(owner, "one"); alert(owner, "two"); alert(other, "other");
        assertThat(call(owner, "/api/notifications/read-all", "PATCH").statusCode()).isEqualTo(204);
        assertThat(count(owner)).isZero(); assertThat(count(other)).isEqualTo(1); assertThat(own()).hasSize(2);
    }
    @Test void deletionRemovesOnlyNotificationAndDoesNotRecreateIt() throws Exception {
        alert(owner, "one"); long id = own().getFirst().getId();
        assertThat(call(owner, "/api/notifications/" + id, "DELETE").statusCode()).isEqualTo(204);
        alert(owner, "one"); assertThat(own()).isEmpty(); assertThat(count(owner)).isZero();
        assertThat(budgets.existsById(budget.getId())).isTrue();
    }
    @Test void newestFirstWithDeterministicIdTieBreakAndPagination() throws Exception {
        alert(owner, "old"); clock.now = clock.now.plusSeconds(1); alert(owner, "new"); alert(owner, "newest");
        var first = mapper.readTree(call(owner, "/api/notifications?size=2", "GET").body());
        assertThat(first.path("notifications").get(0).path("id").asLong()).isEqualTo(own().getLast().getId());
        assertThat(first.path("hasMore").asBoolean()).isTrue();
        assertThat(mapper.readTree(call(owner, "/api/notifications?size=2&page=1", "GET").body()).path("notifications").size()).isEqualTo(1);
        assertThat(call(owner, "/api/notifications?size=500", "GET").statusCode()).isEqualTo(400);
    }
    @Test void warningAtEightyPercentUsesExistingBudgetCalculations() {
        expense("82", TODAY); assertThat(own()).singleElement().satisfies(n -> {
            assertThat(n.getSeverity()).isEqualTo(Notification.Severity.WARNING);
            assertThat(n.getMessage()).isEqualTo("You've used 82% of your Dining budget.");
            assertThat(n.getTargetPath()).isEqualTo("/budgets");
        });
    }
    @Test void roundedPercentDoesNotTriggerWarningBeforeExactThreshold() { expense("79.9999", TODAY); assertThat(own()).isEmpty(); }
    @Test void repeatedChecksAndNewExpensesDoNotDuplicateWarning() {
        expense("80", TODAY); expense("1", TODAY); checkBudget(); checkBudget(); assertThat(own()).hasSize(1);
    }
    @Test void exceededNotificationHasCorrectAmountAndCoexistsWithSingleWarning() {
        expense("80", TODAY); expense("62", TODAY); checkBudget();
        assertThat(own()).hasSize(2); assertThat(own().getLast().getMessage()).isEqualTo("You've exceeded your Dining budget by $42.00.");
        assertThat(own().getLast().getSeverity()).isEqualTo(Notification.Severity.WARNING);
    }
    @Test void exactlyOneHundredPercentShowsReachedLimitWithoutFalseExcess() {
        expense("100", TODAY); checkBudget(); assertThat(own()).singleElement().satisfies(n ->
            assertThat(n.getMessage()).isEqualTo("You've reached your Dining budget limit."));
    }
    @Test void deletedThresholdAlertDoesNotReturnWhenSpendingCrossesAgain() throws Exception {
        var transaction = expense("85", TODAY);
        call(owner, "/api/notifications/" + own().getFirst().getId(), "DELETE");
        transactionService.deleteTransaction(auth(owner), transaction.getId());
        expense("85", TODAY); assertThat(own()).isEmpty();
    }
    @Test void transactionEditsReevaluateOldAndNewCategories() {
        var transaction = transactionService.createTransaction(auth(owner), request("85", TODAY, TransactionCategory.SHOPPING));
        assertThat(own()).isEmpty();
        transactionService.updateTransaction(auth(owner), transaction.getId(), request("85", TODAY, TransactionCategory.DINING));
        assertThat(own()).hasSize(1);
    }
    @Test void historicalTransactionsAndIncomeDoNotTriggerCurrentBudgetAlert() {
        expense("1000", TODAY.minusMonths(1));
        transactionService.createTransaction(auth(owner), TransactionRequest.builder().amount(new BigDecimal("500"))
                .type(TransactionType.INCOME).category(TransactionCategory.SALARY).date(TODAY).build());
        assertThat(own()).isEmpty();
    }
    @Test void notificationInsertionFailureDoesNotRollbackSuccessfulTransaction() {
        doThrow(new IllegalStateException("simulated insertion failure")).when(notifications).saveAndFlush(any());
        var saved = expense("85", TODAY);
        assertThat(transactions.existsById(saved.getId())).isTrue(); assertThat(own()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notification_receipts where user_id=?", Long.class, owner.getId())).isZero();
        reset(notifications); checkBudget(); assertThat(own()).hasSize(1);
    }
    @Test void rolledBackFinancialMutationCreatesNoNotification() {
        var template = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> template.execute(status -> { expense("85", TODAY); throw new IllegalStateException("rollback"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(transactions.findByUserIdOrderByDateDesc(owner.getId())).isEmpty(); assertThat(own()).isEmpty();
    }
    @Test void budgetLimitEditAndNewBudgetTriggerAlertsUsingAlreadyRecordedSpending() {
        expense("60", TODAY); assertThat(own()).isEmpty();
        budgetService.updateBudget(auth(owner), budget.getId(), com.financetracker.backend.dto.BudgetRequest.builder()
                .category(TransactionCategory.DINING).month(YearMonth.from(TODAY)).monthlyLimit(new BigDecimal("70")).build());
        assertThat(own()).hasSize(1);
        transactionService.createTransaction(auth(owner), request("85", TODAY, TransactionCategory.ENTERTAINMENT));
        budgetService.createBudget(auth(owner), com.financetracker.backend.dto.BudgetRequest.builder()
                .category(TransactionCategory.ENTERTAINMENT).month(YearMonth.from(TODAY)).monthlyLimit(new BigDecimal("100")).build());
        assertThat(own()).hasSize(2);
    }
    @Test void upcomingWithinThreeDaysCreatesOnePersistentInfoAlertWithoutGeneratingTransaction() {
        var schedule = schedule(owner, TODAY.plusDays(3), true, null);
        long before = transactions.count(); rules.checkRecurring(schedule.getId()); rules.checkRecurring(schedule.getId());
        assertThat(own()).singleElement().satisfies(n -> {
            assertThat(n.getMessage()).isEqualTo("Rent of $850.00 is scheduled in 3 days.");
            assertThat(n.getSeverity()).isEqualTo(Notification.Severity.INFO);
            assertThat(n.getTargetPath()).isEqualTo("/transactions?tab=upcoming");
        });
        assertThat(transactions.count()).isEqualTo(before);
        assertThat(recurring.findById(schedule.getId()).orElseThrow().getNextOccurrence()).isEqualTo(TODAY.plusDays(3));
    }
    @Test void outsideWindowPausedEndedAndOverdueSchedulesAreIgnored() {
        for (var r : List.of(schedule(owner, TODAY.plusDays(4), true, null), schedule(owner, TODAY.plusDays(2), false, null),
                schedule(owner, TODAY.plusDays(2), true, TODAY.plusDays(1)), schedule(owner, TODAY.minusDays(1), true, null)))
            rules.checkRecurring(r.getId());
        assertThat(own()).isEmpty();
    }
    @Test void dueTodayAndTomorrowWordingAndPerOccurrenceDeduplication() {
        var r = schedule(owner, TODAY, true, null); rules.checkRecurring(r.getId());
        r.setNextOccurrence(TODAY.plusDays(1)); recurring.saveAndFlush(r); rules.checkRecurring(r.getId());
        assertThat(own()).hasSize(2); assertThat(own().getFirst().getMessage()).contains("today"); assertThat(own().getLast().getMessage()).contains("tomorrow");
    }
    @Test void configuredUpcomingWindowAndWarningThresholdAreRespected() {
        properties.setBudgetWarningPercent(new BigDecimal("90")); expense("85", TODAY); assertThat(own()).isEmpty();
        expense("5", TODAY); assertThat(own()).hasSize(1);
        properties.setRecurringUpcomingDays(1); var r = schedule(owner, TODAY.plusDays(2), true, null); rules.checkRecurring(r.getId());
        assertThat(own()).hasSize(1);
    }
    @Test void batchedUpcomingCheckUsesOnlyEligibleSchedulesAndDeduplicates() {
        var active = schedule(owner, TODAY.plusDays(3), true, null);
        var paused = schedule(owner, TODAY.plusDays(2), false, null);
        assertThat(rules.upcomingIds(0)).contains(active.getId()).doesNotContain(paused.getId());
        rules.checkUpcomingBatch(List.of(active.getId(), paused.getId())); rules.checkUpcomingBatch(List.of(active.getId()));
        assertThat(own()).hasSize(1);
    }
    @Test void actualRecurringFailureIsReportedOnceWithSafeErrorText() {
        var r = schedule(owner, TODAY, true, null); r.setLastGeneratedDate(TODAY); recurring.saveAndFlush(r);
        assertThatThrownBy(() -> recurringService.processDueRecurringTransaction(r.getId(), TODAY, 100)).isInstanceOf(IllegalStateException.class);
        events.publishEvent(new FinancialNotificationEvents.RecurringFailed(r.getId()));
        events.publishEvent(new FinancialNotificationEvents.RecurringFailed(r.getId()));
        assertThat(own()).singleElement().satisfies(n -> {
            assertThat(n.getSeverity()).isEqualTo(Notification.Severity.ERROR);
            assertThat(n.getMessage()).isEqualTo("We couldn't process your Rent recurring transaction.");
            assertThat(n.getMessage()).doesNotContain("Exception", "SQL", "already been generated");
        });
        assertThat(recurring.findById(r.getId()).orElseThrow().getNextOccurrence()).isEqualTo(TODAY);
    }
    @Test void recurringGenerationStillCreatesTransactionAndBudgetAlertAfterCommit() {
        var r = schedule(owner, TODAY, true, TODAY); r.setAmount(new BigDecimal("85")); r.setCategory(TransactionCategory.DINING); recurring.saveAndFlush(r);
        assertThat(recurringService.processDueRecurringTransaction(r.getId(), TODAY, 100).generated()).isEqualTo(1);
        assertThat(transactions.findByUserIdOrderByDateDesc(owner.getId())).hasSize(1); assertThat(own()).hasSize(1);
        assertThat(recurring.findById(r.getId()).orElseThrow().isActive()).isFalse();
    }
    @Test void notificationEndpointsDoNotChangeFinancialRecords() throws Exception {
        expense("85", TODAY); var r = schedule(owner, TODAY.plusDays(3), true, null); rules.checkRecurring(r.getId());
        var before = snapshot(); list(owner); count(owner); call(owner, "/api/notifications/" + own().getFirst().getId() + "/read", "PATCH");
        call(owner, "/api/notifications/read-all", "PATCH"); call(owner, "/api/notifications/" + own().getFirst().getId(), "DELETE");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void dataSurvivesNewJwtLoginAndIsStoredInPostgres() throws Exception {
        alert(owner, "persistent"); long id = own().getFirst().getId();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id=?", Long.class, owner.getId())).isEqualTo(1);
        assertThat(list(owner).path("notifications").get(0).path("id").asLong()).isEqualTo(id);
        clock.now = clock.now.plusSeconds(10);
        assertThat(list(owner).path("notifications").get(0).path("id").asLong()).isEqualTo(id);
    }
    @Test void concurrentEventChecksCreateOnlyOneAlert() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> alert(owner, "concurrent")); var b = executor.submit(() -> alert(owner, "concurrent"));
            a.get(); b.get();
        }
        assertThat(own()).hasSize(1);
    }
    private User user(String name) { return users.save(User.builder().username(name).email(name + "@example.invalid").password("unused").build()); }
    private void alert(User user, String key) { notificationService.create(user, key, Notification.Type.BUDGET, Notification.Severity.WARNING, "Budget warning", "Test financial event", "/budgets", budget.getId()); }
    private List<Notification> own() { return notifications.findByUserId(owner.getId(), org.springframework.data.domain.PageRequest.of(0, 50, org.springframework.data.domain.Sort.by("id"))).getContent(); }
    private UsernamePasswordAuthenticationToken auth(User user) { return new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()); }
    private TransactionRequest request(String amount, LocalDate date, TransactionCategory category) { return TransactionRequest.builder().amount(new BigDecimal(amount)).date(date).category(category).type(TransactionType.EXPENSE).build(); }
    private com.financetracker.backend.dto.TransactionResponse expense(String amount, LocalDate date) { return transactionService.createTransaction(auth(owner), request(amount, date, TransactionCategory.DINING)); }
    private void checkBudget() { rules.checkBudgets(new FinancialNotificationEvents.BudgetsChanged(List.of(new FinancialNotificationEvents.BudgetImpact(owner.getId(), TransactionCategory.DINING, TODAY)))); }
    private RecurringTransaction schedule(User user, LocalDate due, boolean active, LocalDate end) { return recurring.saveAndFlush(RecurringTransaction.builder().user(user).amount(new BigDecimal("850")).category(TransactionCategory.HOUSING).type(TransactionType.EXPENSE).description("Rent").frequency(RecurringFrequency.MONTHLY).startDate(due.minusMonths(1)).nextOccurrence(due).active(active).endDate(end).build()); }
    private tools.jackson.databind.JsonNode list(User user) throws Exception { var response = call(user, "/api/notifications", "GET"); assertThat(response.statusCode()).isEqualTo(200); return mapper.readTree(response.body()); }
    private long count(User user) throws Exception { return mapper.readTree(call(user, "/api/notifications/unread-count", "GET").body()).path("count").asLong(); }
    private List<List<Map<String, Object>>> snapshot() { return List.of("transactions", "budgets", "recurring_transactions").stream().map(table -> jdbc.queryForList("select * from " + table + " where user_id=? order by id", owner.getId())).toList(); }
    private HttpResponse<String> call(User user, String path, String method) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.noBody());
        if (user != null) builder.header("Authorization", "Bearer " + jwt.generateToken(user));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    @TestConfiguration static class TestClock { @Bean @Primary MutableClock notificationClock() { return new MutableClock(); } }
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-15T16:00:00Z");
        public ZoneId getZone() { return ZoneId.of("America/Toronto"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
