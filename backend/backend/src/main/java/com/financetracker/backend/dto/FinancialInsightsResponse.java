package com.financetracker.backend.dto;

import java.time.Instant;
import java.util.List;

public record FinancialInsightsResponse(Instant generatedAt, List<FinancialInsightResponse> insights) {}
