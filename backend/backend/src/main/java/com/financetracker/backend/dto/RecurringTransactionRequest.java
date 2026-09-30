package com.financetracker.backend.dto;

import com.financetracker.backend.entities.RecurringFrequency;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class RecurringTransactionRequest {

    @NotNull
    @DecimalMin("0.0001")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotNull
    private TransactionCategory category;

    @NotNull
    private TransactionType type;

    @NotBlank
    @Size(max = 500)
    private String description;

    @Size(max = 150)
    private String merchant;

    @NotNull
    private RecurringFrequency frequency;

    @NotNull
    private LocalDate startDate;

    private LocalDate endDate;
}
