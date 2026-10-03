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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@SpringBootTest(properties = "recurring.scheduler.enabled=false")
class RecurringForecastIntegrationTests {

    @Autowired private UserRepository userRepository;
    @Autowired private RecurringTransactionRepository recurringRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RecurringForecastService forecastService;

    private User owner;
    private User other;
    private RecurringTransaction ownerSchedule;
    private RecurringTransaction otherSchedule;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        owner = userRepository.save(User.builder().username("forecast_owner_" + suffix)
                .email("forecast_owner_" + suffix + "@example.com").password("unused").build());
        other = userRepository.save(User.builder().username("forecast_other_" + suffix)
                .email("forecast_other_" + suffix + "@example.com").password("unused").build());
        ownerSchedule = recurringRepository.save(schedule(owner, "Netflix", LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 30)));
        otherSchedule = recurringRepository.save(schedule(other, "Other user rent", LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 10, 15)));
        transactionRepository.save(Transaction.builder().user(owner).amount(new BigDecimal("50.00"))
                .category(TransactionCategory.ENTERTAINMENT).type(TransactionType.EXPENSE)
                .description("Netflix").merchant("Netflix").date(LocalDate.of(2026, 9, 30)).build());
    }

    @AfterEach
    void cleanUp() {
        for (User user : new User[] { owner, other }) {
            if (user == null) continue;
            transactionRepository.deleteAll(transactionRepository.findByUserIdOrderByDateDesc(user.getId()));
        }
        if (ownerSchedule != null) recurringRepository.deleteById(ownerSchedule.getId());
        if (otherSchedule != null) recurringRepository.deleteById(otherSchedule.getId());
        if (owner != null) userRepository.deleteById(owner.getId());
        if (other != null) userRepository.deleteById(other.getId());
    }

    @Test
    void forecastsAreOwnedAndDoNotCreateTransactionsOrChangeSchedules() {
        var authentication = new UsernamePasswordAuthenticationToken(owner.getEmail(), null);
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);

        var upcoming = forecastService.getUpcoming(authentication, from, to);
        var forecast = forecastService.getForecast(authentication, from, to);

        assertThat(upcoming).extracting(item -> item.recurringTransactionId())
                .containsExactly(ownerSchedule.getId(), ownerSchedule.getId());
        assertThat(upcoming).extracting(item -> item.scheduledDate())
                .containsExactly(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 30));
        assertThat(forecast.expectedExpenses()).isEqualByComparingTo("100.00");
        assertThat(forecast.expectedIncome()).isEqualByComparingTo("0");
        assertThat(transactionRepository.findByUserIdOrderByDateDesc(owner.getId()))
                .extracting(Transaction::getDate).containsExactly(LocalDate.of(2026, 9, 30));

        RecurringTransaction persisted = recurringRepository.findById(ownerSchedule.getId()).orElseThrow();
        assertThat(persisted.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(persisted.getLastGeneratedDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(persisted.isActive()).isTrue();
    }

    private RecurringTransaction schedule(User user, String description, LocalDate start, LocalDate next) {
        return RecurringTransaction.builder().user(user).amount(new BigDecimal("50.00"))
                .category(TransactionCategory.ENTERTAINMENT).type(TransactionType.EXPENSE)
                .description(description).merchant(description).frequency(RecurringFrequency.MONTHLY)
                .startDate(start).nextOccurrence(next).lastGeneratedDate(start).build();
    }
}
