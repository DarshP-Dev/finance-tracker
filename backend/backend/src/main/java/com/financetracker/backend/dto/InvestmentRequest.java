package com.financetracker.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
public class InvestmentRequest {

    @NotBlank(message = "Ticker cannot be empty")
    @Size(max = 20, message = "Ticker must be 20 characters or fewer")
    @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9.-]*",
            message = "Ticker may contain letters, numbers, periods, and hyphens"
    )
    private String ticker;

    @NotNull(message = "Shares are required")
    @Positive(message = "Shares must be greater than 0")
    private BigDecimal shares;

    @NotNull(message = "Purchase price is required")
    @Positive(message = "Purchase price must be greater than 0")
    private BigDecimal purchasePrice;

    @NotNull(message = "Purchase date is required")
    @PastOrPresent(message = "Purchase date cannot be in the future")
    private LocalDate purchaseDate;
}
