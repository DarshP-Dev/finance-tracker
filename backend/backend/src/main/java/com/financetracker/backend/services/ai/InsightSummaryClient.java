package com.financetracker.backend.services.ai;

import com.financetracker.backend.dto.FinancialInsightResponse.Severity;
import com.financetracker.backend.dto.FinancialInsightResponse.Type;
import java.util.List;

/** Provider boundary: receives derived text only, never repositories or authentication. */
public interface InsightSummaryClient {
    boolean isConfigured();
    String summarize(List<SourceInsight> insights);

    record SourceInsight(Type type, Severity severity, String title, String message) {}

    class ProviderUnavailableException extends RuntimeException {
        public ProviderUnavailableException() { super("AI summary provider unavailable"); }
    }
}
