package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.backend.entities.RecurringFrequency;
import com.financetracker.backend.entities.RecurringTransaction;
import com.financetracker.backend.entities.Transaction;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.RecurringTransactionRepository;
import com.financetracker.backend.repositories.TransactionRepository;
import com.financetracker.backend.repositories.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = "recurring.scheduler.enabled=false")
class RecurringTransactionConcurrencyTests {

    private static final LocalDate START = LocalDate.of(2026, 9, 30);
    private static final LocalDate DUE = LocalDate.of(2026, 10, 30);

    @Autowired private UserRepository userRepository;
    @Autowired private RecurringTransactionRepository recurringRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RecurringTransactionService service;

    private User user;
    private RecurringTransaction recurring;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        user = userRepository.save(User.builder()
                .username("recurring_test_" + suffix)
                .email("recurring_test_" + suffix + "@example.com")
                .password("unused")
                .build());
        recurring = recurringRepository.save(RecurringTransaction.builder()
                .user(user).amount(new BigDecimal("50.00"))
                .category(TransactionCategory.ENTERTAINMENT).type(TransactionType.EXPENSE)
                .description("Netflix").merchant("Netflix").frequency(RecurringFrequency.MONTHLY)
                .startDate(START).lastGeneratedDate(START).nextOccurrence(DUE).endDate(DUE)
                .build());
        transactionRepository.save(Transaction.builder()
                .user(user).amount(new BigDecimal("50.00"))
                .category(TransactionCategory.ENTERTAINMENT).type(TransactionType.EXPENSE)
                .description("Netflix").merchant("Netflix").date(START)
                .build());
    }

    @AfterEach
    void cleanUp() {
        if (user != null) {
            transactionRepository.deleteAll(transactionRepository.findByUserIdOrderByDateDesc(user.getId()));
            if (recurring != null) {
                recurringRepository.deleteById(recurring.getId());
            }
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void concurrentAutomaticRunsCreateOnlyOneChargeForTheDueDate() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RecurringTransactionService.DueProcessingResult> first = executor.submit(() -> {
                start.await();
                return service.processDueRecurringTransaction(recurring.getId(), DUE, 100);
            });
            Future<RecurringTransactionService.DueProcessingResult> second = executor.submit(() -> {
                start.await();
                return service.processDueRecurringTransaction(recurring.getId(), DUE, 100);
            });
            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS).generated()
                    + second.get(30, TimeUnit.SECONDS).generated()).isEqualTo(1);
            assertFinalState();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void dueQueryExcludesPausedAndFutureDefinitions() {
        var page = PageRequest.of(0, 1000);
        assertThat(recurringRepository.findDueIds(DUE, 0L, page)).contains(recurring.getId());

        recurring.setActive(false);
        recurring = recurringRepository.saveAndFlush(recurring);
        assertThat(recurringRepository.findDueIds(DUE, 0L, page)).doesNotContain(recurring.getId());

        recurring.setActive(true);
        recurring.setNextOccurrence(DUE.plusDays(1));
        recurring = recurringRepository.saveAndFlush(recurring);
        assertThat(recurringRepository.findDueIds(DUE, 0L, page)).doesNotContain(recurring.getId());
    }

    @Test
    void manualRequestAndAutomaticRunCannotChargeTheSameDateTwice() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null);
        try {
            Future<?> manual = executor.submit(() -> {
                start.await();
                try {
                    service.generateNextTransaction(authentication, recurring.getId());
                } catch (ResponseStatusException exception) {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                }
                return null;
            });
            Future<?> automatic = executor.submit(() -> {
                start.await();
                service.processDueRecurringTransaction(recurring.getId(), DUE, 100);
                return null;
            });
            start.countDown();

            manual.get(30, TimeUnit.SECONDS);
            automatic.get(30, TimeUnit.SECONDS);
            assertFinalState();
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertFinalState() {
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByDateDesc(user.getId());
        assertThat(transactions).extracting(Transaction::getDate).containsExactly(DUE, START);
        RecurringTransaction persisted = recurringRepository.findById(recurring.getId()).orElseThrow();
        assertThat(persisted.getLastGeneratedDate()).isEqualTo(DUE);
        assertThat(persisted.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(persisted.isActive()).isFalse();
    }
}
