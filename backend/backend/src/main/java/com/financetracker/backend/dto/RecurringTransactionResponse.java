package com.financetracker.backend.dto;

import com.financetracker.backend.entities.RecurringFrequency;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTransactionResponse {

    private Long id;
    private BigDecimal amount;
    private TransactionCategory category;
    private TransactionType type;
    private String description;
    private String merchant;
    private RecurringFrequency frequency;
    private LocalDate startDate;
    private LocalDate nextOccurrence;
    private LocalDate endDate;
    private boolean active;
    private LocalDateTime createdAt;
}
