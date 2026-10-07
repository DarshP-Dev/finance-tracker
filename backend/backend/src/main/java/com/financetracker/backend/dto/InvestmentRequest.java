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

    private String ticker;

    @NotBlank(message = "Ticker cannot be empty")
    @Size(max = 20, message = "Ticker must be 20 characters or fewer")
    @Pattern(regexp = "[A-Z0-9][A-Z0-9.-]*", message = "Ticker may contain letters, numbers, periods, and hyphens")
    public String getTicker() { return ticker == null ? null : ticker.trim().toUpperCase(java.util.Locale.ROOT); }

    public void setTicker(String ticker) {
        this.ticker = ticker == null ? null : ticker.trim().toUpperCase(java.util.Locale.ROOT);
    }

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
