package com.financetracker.backend.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** No toString: configuration contains an API credential. */
@Component
@ConfigurationProperties(prefix = "ai.insights")
@Getter
@Setter
public class InsightsAiProperties {
    private boolean enabled = false;
    private String apiKey = "";
    private String model = "gemini-3.5-flash-lite";
    private String endpoint = "https://generativelanguage.googleapis.com/v1beta/models";
    private int maxOutputLength = 1000;
    private int maxOutputTokens = 384;
    private Duration timeout = Duration.ofSeconds(8);
    private Duration cacheTtl = Duration.ofMinutes(10);
    private Duration minRequestInterval = Duration.ofSeconds(30);
    private int maxCacheUsers = 256;
}
