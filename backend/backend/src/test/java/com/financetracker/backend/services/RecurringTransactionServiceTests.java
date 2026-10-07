package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financetracker.backend.dto.RecurringTransactionRequest;
import com.financetracker.backend.dto.RecurringTransactionResponse;
import com.financetracker.backend.dto.TransactionRequest;
import com.financetracker.backend.dto.TransactionResponse;
import com.financetracker.backend.entities.RecurringFrequency;
import com.financetracker.backend.entities.RecurringTransaction;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.RecurringTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceTests {

    private static final LocalDate START = LocalDate.of(2026, 10, 12);

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private RecurringTransactionRepository recurringTransactionRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private Authentication authentication;

    private RecurringTransactionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RecurringTransactionService(authenticatedUserService, recurringTransactionRepository, transactionService,
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class));
        user = User.builder().id(25L).email("owner@example.com").build();
        when(authenticatedUserService.getCurrentUser(authentication)).thenReturn(user);
    }

    @Test
    void createsTheFirstTransactionAndAdvancesTheSchedule() {
        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionResponse response = service.createRecurringTransaction(authentication, request(START, null));

        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(recurringTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        ArgumentCaptor<TransactionRequest> transactionCaptor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).createTransactionForUser(org.mockito.ArgumentMatchers.eq(user), transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getDate()).isEqualTo(START);
        assertThat(transactionCaptor.getValue().getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(transactionCaptor.getValue().getAmount()).isEqualByComparingTo("22.99");
        assertThat(captor.getValue().getLastGeneratedDate()).isEqualTo(START);
        assertThat(response.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void creatingASingleOccurrenceRecordsItAndClosesTheSchedule() {
        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionResponse response = service.createRecurringTransaction(authentication, request(START, START));

        verify(transactionService).createTransactionForUser(any(), any());
        assertThat(response.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void listsOnlyTheAuthenticatedUsersDefinitions() {
        when(recurringTransactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(25L))
                .thenReturn(List.of(recurring(10L, null)));

        assertThat(service.getRecurringTransactions(authentication)).hasSize(1);
        verify(recurringTransactionRepository).findByUserIdOrderByCreatedAtDescIdDesc(25L);
    }

    @Test
    void generatesNormalTransactionsAndAdvancesToTheFollowingOccurrences() {
        RecurringTransaction recurring = recurring(10L, LocalDate.of(2026, 11, 12));
        when(recurringTransactionRepository.findByIdAndUserIdForUpdate(10L, 25L)).thenReturn(Optional.of(recurring));
        when(transactionService.createTransactionForUser(any(), any()))
                .thenAnswer(invocation -> TransactionResponse.builder()
                        .date(((TransactionRequest) invocation.getArgument(1)).getDate()).build());

        TransactionResponse first = service.generateNextTransaction(authentication, 10L);
        TransactionResponse second = service.generateNextTransaction(authentication, 10L);

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService, org.mockito.Mockito.times(2)).createTransactionForUser(
                org.mockito.ArgumentMatchers.eq(user), captor.capture());
        assertThat(captor.getAllValues()).extracting(TransactionRequest::getDate)
                .containsExactly(START, LocalDate.of(2026, 11, 12));
        assertThat(captor.getAllValues().getFirst().getAmount()).isEqualByComparingTo("22.99");
        assertThat(first.getDate()).isEqualTo(START);
        assertThat(second.getDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(recurring.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 12, 12));
        assertThat(recurring.isActive()).isFalse();
    }

    @Test
    void cannotGenerateOrModifyAnotherUsersDefinition() {
        when(recurringTransactionRepository.findByIdAndUserIdForUpdate(90L, 25L))
                .thenReturn(Optional.empty());

        assertStatus(() -> service.generateNextTransaction(authentication, 90L), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.pauseRecurringTransaction(authentication, 90L), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.resumeRecurringTransaction(authentication, 90L), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.deleteRecurringTransaction(authentication, 90L), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.updateRecurringTransaction(authentication, 90L, request(START, null)),
                HttpStatus.NOT_FOUND);
        verify(transactionService, never()).createTransactionForUser(any(), any());
    }

    @Test
    void editingScheduleDoesNotRevisitAnAlreadyGeneratedDate() {
        RecurringTransaction recurring = recurring(10L, null);
        recurring.setLastGeneratedDate(START);
        recurring.setNextOccurrence(LocalDate.of(2026, 11, 12));
        when(recurringTransactionRepository.findByIdAndUserIdForUpdate(10L, 25L)).thenReturn(Optional.of(recurring));
        RecurringTransactionRequest changed = request(LocalDate.of(2026, 10, 1), null);
        changed.setFrequency(RecurringFrequency.WEEKLY);

        RecurringTransactionResponse response = service.updateRecurringTransaction(authentication, 10L, changed);

        assertThat(response.getNextOccurrence()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void pauseBlocksGenerationAndResumeRestoresIt() {
        RecurringTransaction recurring = recurring(10L, null);
        when(recurringTransactionRepository.findByIdAndUserIdForUpdate(10L, 25L)).thenReturn(Optional.of(recurring));

        assertThat(service.pauseRecurringTransaction(authentication, 10L).isActive()).isFalse();
        assertStatus(() -> service.generateNextTransaction(authentication, 10L), HttpStatus.CONFLICT);
        assertThat(service.resumeRecurringTransaction(authentication, 10L).isActive()).isTrue();
        verify(transactionService, never()).createTransactionForUser(any(), any());
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        assertStatus(() -> service.createRecurringTransaction(
                authentication, request(START, START.minusDays(1))), HttpStatus.BAD_REQUEST);
        verify(recurringTransactionRepository, never()).save(any());
    }

    private RecurringTransactionRequest request(LocalDate startDate, LocalDate endDate) {
        return RecurringTransactionRequest.builder()
                .amount(new BigDecimal("22.99"))
                .category(TransactionCategory.ENTERTAINMENT)
                .type(TransactionType.EXPENSE)
                .description("Netflix")
                .merchant("Netflix")
                .frequency(RecurringFrequency.MONTHLY)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    private RecurringTransaction recurring(Long id, LocalDate endDate) {
        return RecurringTransaction.builder()
                .id(id)
                .user(user)
                .amount(new BigDecimal("22.99"))
                .category(TransactionCategory.ENTERTAINMENT)
                .type(TransactionType.EXPENSE)
                .description("Netflix")
                .merchant("Netflix")
                .frequency(RecurringFrequency.MONTHLY)
                .startDate(START)
                .nextOccurrence(START)
                .endDate(endDate)
                .build();
    }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(status));
    }
}
