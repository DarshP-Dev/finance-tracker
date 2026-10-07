package com.financetracker.backend.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-only optional credentials; deliberately no toString. */
@Component
@ConfigurationProperties(prefix = "market-data")
@Getter @Setter
public class MarketDataProperties {
    private boolean enabled = false;
    private String provider = "twelve-data";
    private String apiKey = "";
    private String endpoint = "https://api.twelvedata.com/quote";
    private Duration cacheDuration = Duration.ofMinutes(10);
    private Duration staleDuration = Duration.ofHours(24);
    private Duration timeout = Duration.ofSeconds(6);
    private int creditsPerMinute = 8;
    private int creditsPerDay = 800;
    private int maxCacheSymbols = 512;
    private int maxRequestsPerMinute = 8;
    private int maxRequestsPerDay = 750;
    private int dailyReserve = 50;
    private Duration refreshCooldown = Duration.ofSeconds(120);
    private Duration negativeCacheDuration = Duration.ofMinutes(30);
    private Duration providerBackoff = Duration.ofMinutes(5);
    private Duration unchangedQuoteCacheDuration = Duration.ofMinutes(30);
}
