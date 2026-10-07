package com.financetracker.backend.services;

import com.financetracker.backend.dto.RecurringTransactionRequest;
import com.financetracker.backend.dto.RecurringTransactionResponse;
import com.financetracker.backend.dto.TransactionRequest;
import com.financetracker.backend.dto.TransactionResponse;
import com.financetracker.backend.entities.RecurringTransaction;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.RecurringTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RecurringTransactionService {

    private final AuthenticatedUserService authenticatedUserService;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher events;

    @Transactional
    public RecurringTransactionResponse createRecurringTransaction(
            Authentication authentication, RecurringTransactionRequest request
    ) {
        User user = authenticatedUserService.getCurrentUser(authentication);
        validate(request);

        // The first occurrence is recorded when the user creates the schedule.
        RecurringTransaction recurring = RecurringTransaction.builder()
                .user(user)
                .amount(request.getAmount())
                .category(TransactionCategoryNormalizer.normalize(request.getCategory()))
                .type(request.getType())
                .description(request.getDescription().trim())
                .merchant(normalizeBlank(request.getMerchant()))
                .frequency(request.getFrequency())
                .startDate(request.getStartDate())
                .nextOccurrence(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        recurringTransactionRepository.save(recurring);
        generateOccurrence(recurring);
        events.publishEvent(new FinancialNotificationEvents.RecurringChanged(recurring.getId()));
        return toResponse(recurring);
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> getRecurringTransactions(Authentication authentication) {
        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        return recurringTransactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecurringTransactionResponse getRecurringTransactionById(Authentication authentication, Long id) {
        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        return toResponse(getOwned(id, userId));
    }

    @Transactional
    public RecurringTransactionResponse updateRecurringTransaction(
            Authentication authentication, Long id, RecurringTransactionRequest request
    ) {
        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        RecurringTransaction recurring = getOwnedForUpdate(id, userId);
        validate(request);

        boolean scheduleChanged = !recurring.getStartDate().equals(request.getStartDate())
                || recurring.getFrequency() != request.getFrequency();

        recurring.setAmount(request.getAmount());
        recurring.setCategory(TransactionCategoryNormalizer.normalize(request.getCategory()));
        recurring.setType(request.getType());
        recurring.setDescription(request.getDescription().trim());
        recurring.setMerchant(normalizeBlank(request.getMerchant()));
        recurring.setFrequency(request.getFrequency());
        recurring.setStartDate(request.getStartDate());
        recurring.setEndDate(request.getEndDate());

        if (scheduleChanged) {
            // Never revisit a generated date when editing a schedule.
            recurring.setNextOccurrence(RecurringDateCalculator.firstAfter(
                    request.getStartDate(), recurring.getLastGeneratedDate(), request.getFrequency()));
        }
        if (pastEndDate(recurring)) {
            recurring.setActive(false);
        }

        events.publishEvent(new FinancialNotificationEvents.RecurringChanged(recurring.getId()));

        return toResponse(recurring);
    }

    @Transactional
    public void deleteRecurringTransaction(Authentication authentication, Long id) {
        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        recurringTransactionRepository.delete(getOwnedForUpdate(id, userId));
    }

    @Transactional
    public RecurringTransactionResponse pauseRecurringTransaction(Authentication authentication, Long id) {
        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        RecurringTransaction recurring = getOwnedForUpdate(id, userId);
        recurring.setActive(false);
        return toResponse(recurring);
    }

    @Transactional
    public RecurringTransactionResponse resumeRecurringTransaction(Authentication authentication, Long id) {
        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        RecurringTransaction recurring = getOwnedForUpdate(id, userId);
        if (pastEndDate(recurring)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recurring transaction has passed its end date");
        }
        recurring.setActive(true);
        events.publishEvent(new FinancialNotificationEvents.RecurringChanged(recurring.getId()));
        return toResponse(recurring);
    }

    @Transactional
    public TransactionResponse generateNextTransaction(Authentication authentication, Long id) {
        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        // Row locking serializes concurrent generate, update, pause, resume and delete calls for this definition.
        RecurringTransaction recurring = getOwnedForUpdate(id, userId);
        if (!recurring.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recurring transaction is inactive");
        }
        if (pastEndDate(recurring)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recurring transaction has passed its end date");
        }

        TransactionResponse result = generateOccurrence(recurring);
        events.publishEvent(new FinancialNotificationEvents.RecurringChanged(recurring.getId()));
        return result;
    }

    /** Called through the Spring proxy so each definition has its own transaction and row lock. */
    @Transactional
    public DueProcessingResult processDueRecurringTransaction(Long id, LocalDate today, int maxCatchUp) {
        if (maxCatchUp < 1) {
            throw new IllegalArgumentException("maxCatchUp must be positive");
        }
        RecurringTransaction recurring = recurringTransactionRepository.findByIdForUpdate(id).orElse(null);
        if (recurring == null || !recurring.isActive() || recurring.getNextOccurrence().isAfter(today)) {
            return new DueProcessingResult(0, false, false);
        }
        if (pastEndDate(recurring)) {
            recurring.setActive(false);
            return new DueProcessingResult(0, false, true);
        }

        int generated = 0;
        while (recurring.isActive() && !recurring.getNextOccurrence().isAfter(today) && generated < maxCatchUp) {
            generateOccurrence(recurring);
            generated++;
        }
        boolean limitReached = recurring.isActive() && !recurring.getNextOccurrence().isAfter(today);
        return new DueProcessingResult(generated, limitReached, !recurring.isActive());
    }

    public record DueProcessingResult(int generated, boolean limitReached, boolean expired) {
    }

    private TransactionResponse generateOccurrence(RecurringTransaction recurring) {
        LocalDate occurrence = recurring.getNextOccurrence();
        if (recurring.getLastGeneratedDate() != null && !occurrence.isAfter(recurring.getLastGeneratedDate())) {
            throw new IllegalStateException("Recurring occurrence has already been generated");
        }
        TransactionRequest request = TransactionRequest.builder()
                .amount(recurring.getAmount())
                .category(recurring.getCategory())
                .type(recurring.getType())
                .description(recurring.getDescription())
                .merchant(recurring.getMerchant())
                .date(occurrence)
                .build();
        TransactionResponse generated = transactionService.createTransactionForUser(recurring.getUser(), request);

        recurring.setLastGeneratedDate(occurrence);
        recurring.setNextOccurrence(RecurringDateCalculator.next(
                recurring.getStartDate(), occurrence, recurring.getFrequency()));
        if (pastEndDate(recurring)) {
            recurring.setActive(false);
        }
        return generated;
    }

    private RecurringTransaction getOwned(Long id, Long userId) {
        return recurringTransactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring transaction not found"));
    }

    private RecurringTransaction getOwnedForUpdate(Long id, Long userId) {
        return recurringTransactionRepository.findByIdAndUserIdForUpdate(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring transaction not found"));
    }

    private void validate(RecurringTransactionRequest request) {
        if (request == null || request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getCategory() == null || request.getType() == null
                || request.getFrequency() == null || request.getStartDate() == null
                || request.getDescription() == null || request.getDescription().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid recurring transaction details");
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate");
        }
    }

    private boolean pastEndDate(RecurringTransaction recurring) {
        return recurring.getEndDate() != null && recurring.getNextOccurrence().isAfter(recurring.getEndDate());
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RecurringTransactionResponse toResponse(RecurringTransaction recurring) {
        return RecurringTransactionResponse.builder()
                .id(recurring.getId())
                .amount(recurring.getAmount())
                .category(recurring.getCategory())
                .type(recurring.getType())
                .description(recurring.getDescription())
                .merchant(recurring.getMerchant())
                .frequency(recurring.getFrequency())
                .startDate(recurring.getStartDate())
                .nextOccurrence(recurring.getNextOccurrence())
                .endDate(recurring.getEndDate())
                .active(recurring.isActive())
                .createdAt(recurring.getCreatedAt())
                .build();
    }
}
