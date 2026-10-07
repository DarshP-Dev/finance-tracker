package com.financetracker.backend.services;

import static com.financetracker.backend.dto.FinancialInsightsSummaryResponse.Status.*;

import com.financetracker.backend.config.InsightsAiProperties;
import com.financetracker.backend.dto.FinancialInsightResponse;
import com.financetracker.backend.dto.FinancialInsightsSummaryResponse;
import com.financetracker.backend.dto.FinancialInsightsSummaryResponse.Status;
import com.financetracker.backend.services.ai.InsightSummaryClient;
import com.financetracker.backend.services.ai.InsightSummaryClient.SourceInsight;
import com.financetracker.backend.services.ai.InsightSummaryValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Financial reads finish before outbound HTTP; no transaction or connection is held while waiting. */
@Service
@RequiredArgsConstructor
public class FinancialInsightsSummaryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialInsightsSummaryService.class);
    private final AuthenticatedUserService users;
    private final FinancialInsightsService insights;
    private final InsightSummaryClient client;
    private final InsightSummaryValidator validator;
    private final InsightsAiProperties properties;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final Map<Long, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Object[] userLocks = IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public FinancialInsightsSummaryResponse generateSummary(Authentication authentication) {
        Long userId = users.getCurrentUser(authentication).getId();
        if (!properties.isEnabled()) return response(null, 0, DISABLED, 0);
        synchronized (userLocks[Math.floorMod(userId.hashCode(), userLocks.length)]) {
            // Always re-read canonical insights so changed data cannot reuse an old narrative.
            var source = insights.generateInsightsForUser(authentication);
            if (source.insights().isEmpty()) return response("There isn't enough financial activity yet to generate a summary.", 0, EMPTY, 0);
            List<SourceInsight> minimized = source.insights().stream().map(this::minimize).toList();
            if (minimized.size() == 1) return response(minimized.getFirst().message(), 1, DETERMINISTIC, 0);
            if (!client.isConfigured()) return response(null, minimized.size(), UNAVAILABLE, 0);
            String fingerprint;
            try { fingerprint = fingerprint(minimized); }
            catch (RuntimeException exception) { return response(null, minimized.size(), UNAVAILABLE, 0); }
            Instant now = clock.instant();
            CacheEntry previous;
            synchronized (cache) { previous = cache.get(userId); }
            if (previous != null) {
                if (previous.fingerprint().equals(fingerprint) && now.isBefore(previous.expiresAt())) {
                    if (previous.response().aiGenerated()) return previous.response();
                    return response(null, minimized.size(), UNAVAILABLE,
                            (int) Math.max(1, Duration.between(now, previous.nextRequestAt()).toSeconds() + 1));
                }
                if (now.isBefore(previous.nextRequestAt())) {
                    return response(null, minimized.size(), COOLDOWN,
                            (int) Math.max(1, Duration.between(now, previous.nextRequestAt()).toSeconds() + 1));
                }
            }
            FinancialInsightsSummaryResponse result;
            try {
                String text = client.summarize(minimized);
                var rejection = validator.rejectionReason(text, minimized, properties.getMaxOutputLength());
                if (rejection != InsightSummaryValidator.RejectionReason.NONE) {
                    LOGGER.warn("Gemini summary output rejected by financial insight validation (reason {})", rejection);
                    throw new IllegalArgumentException("Invalid summary");
                }
                result = response(text.strip(), minimized.size(), AVAILABLE, 0);
                LOGGER.info("AI insight summary request succeeded");
            } catch (RuntimeException exception) {
                result = response(null, minimized.size(), UNAVAILABLE, cooldownSeconds());
                LOGGER.warn("AI insight summary unavailable");
            }
            Instant completed = clock.instant();
            Duration ttl = result.aiGenerated() ? boundedTtl() : Duration.ofSeconds(cooldownSeconds());
            synchronized (cache) {
                cache.entrySet().removeIf(e -> !completed.isBefore(e.getValue().expiresAt()) && !completed.isBefore(e.getValue().nextRequestAt()));
                cache.put(userId, new CacheEntry(fingerprint, result, completed.plus(ttl), completed.plusSeconds(cooldownSeconds())));
                int maximum = Math.clamp(properties.getMaxCacheUsers(), 1, 10_000);
                while (cache.size() > maximum) cache.remove(cache.keySet().iterator().next());
            }
            return result;
        }
    }

    private SourceInsight minimize(FinancialInsightResponse insight) {
        String message = insight.message();
        // This one template includes a user-entered description. Do not transmit it.
        if (insight.key().equals("upcoming-largest-expense")) {
            int suffix = message.lastIndexOf(" of $");
            message = suffix < 0 ? "A recurring expense is scheduled soon." : "A recurring expense" + message.substring(suffix);
        }
        return new SourceInsight(insight.type(), insight.severity(), insight.title(), message);
    }

    private String fingerprint(List<SourceInsight> source) {
        try {
            String serialized = mapper.writeValueAsString(source);
            if (serialized.length() > 16_000) throw new IllegalArgumentException("Insight data too long");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((LocalDate.now(clock) + serialized).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("Unable to prepare summary"); }
    }

    private FinancialInsightsSummaryResponse response(String text, int count, Status status, int retryAfter) {
        return new FinancialInsightsSummaryResponse(text, clock.instant(), count, status == AVAILABLE, status, retryAfter);
    }
    private int cooldownSeconds() {
        return (int) Math.clamp(properties.getMinRequestInterval() == null ? 30 : properties.getMinRequestInterval().toSeconds(), 1, 300);
    }
    private Duration boundedTtl() {
        long seconds = properties.getCacheTtl() == null ? 600 : properties.getCacheTtl().toSeconds();
        return Duration.ofSeconds(Math.clamp(seconds, 1, 3600));
    }
    private record CacheEntry(String fingerprint, FinancialInsightsSummaryResponse response,
                              Instant expiresAt, Instant nextRequestAt) {}
}
