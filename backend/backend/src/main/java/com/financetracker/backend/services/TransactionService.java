package com.financetracker.backend.services;

import com.financetracker.backend.dto.TransactionRequest;
import com.financetracker.backend.dto.TransactionResponse;
import com.financetracker.backend.entities.Transaction;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.TransactionRepository;
import com.financetracker.backend.repositories.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public TransactionResponse createTransaction(Authentication authentication, TransactionRequest request) {
        User user = getAuthenticatedUser(authentication);

        Transaction transaction = Transaction.builder()
                .user(user)
                .amount(request.getAmount())
                .category(request.getCategory())
                .type(request.getType())
                .description(normalizeBlank(request.getDescription()))
                .date(request.getDate())
                .merchant(normalizeBlank(request.getMerchant()))
                .build();

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(
            Authentication authentication,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate,
            TransactionCategory category,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        User user = getAuthenticatedUser(authentication);
        validateFilters(startDate, endDate, minAmount, maxAmount);

        return transactionRepository.findAll(
                        buildFilter(user.getId(), date, startDate, endDate, category, minAmount, maxAmount),
                        Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "id"))
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TransactionResponse updateTransaction(
            Authentication authentication,
            Long transactionId,
            TransactionRequest request
    ) {
        User user = getAuthenticatedUser(authentication);
        Transaction transaction = getOwnedTransaction(transactionId, user.getId());

        transaction.setAmount(request.getAmount());
        transaction.setCategory(request.getCategory());
        transaction.setType(request.getType());
        transaction.setDescription(normalizeBlank(request.getDescription()));
        transaction.setDate(request.getDate());
        transaction.setMerchant(normalizeBlank(request.getMerchant()));

        return toResponse(transaction);
    }

    @Transactional
    public void deleteTransaction(Authentication authentication, Long transactionId) {
        User user = getAuthenticatedUser(authentication);
        Transaction transaction = getOwnedTransaction(transactionId, user.getId());
        transactionRepository.delete(transaction);
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }

        String email = authentication.getName().toLowerCase(Locale.ROOT).trim();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private Transaction getOwnedTransaction(Long transactionId, Long userId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    private Specification<Transaction> buildFilter(
            Long userId,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate,
            TransactionCategory category,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));

            if (date != null) {
                predicates.add(criteriaBuilder.equal(root.get("date"), date));
            } else {
                if (startDate != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), startDate));
                }

                if (endDate != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), endDate));
                }
            }

            if (category != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), category));
            }

            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }

            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validateFilters(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate");
        }

        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minAmount must be less than or equal to maxAmount");
        }
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .category(transaction.getCategory())
                .type(transaction.getType())
                .description(transaction.getDescription())
                .date(transaction.getDate())
                .merchant(transaction.getMerchant())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
