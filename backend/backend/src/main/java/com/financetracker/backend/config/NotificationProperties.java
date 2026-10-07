package com.financetracker.backend.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @ConfigurationProperties(prefix = "notifications") @Getter @Setter
public class NotificationProperties {
    private BigDecimal budgetWarningPercent = BigDecimal.valueOf(80);
    private int recurringUpcomingDays = 3;

    public BigDecimal warningPercent() {
        return budgetWarningPercent == null || budgetWarningPercent.signum() <= 0
                || budgetWarningPercent.compareTo(BigDecimal.valueOf(100)) >= 0
                ? BigDecimal.valueOf(80) : budgetWarningPercent;
    }
    public int upcomingDays() { return Math.clamp(recurringUpcomingDays, 0, 30); }
}
