package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.FinancialInsightsSummaryResponse;
import com.financetracker.backend.services.FinancialInsightsSummaryService;
import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import com.financetracker.backend.services.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/financial-insights/summary")
@RequiredArgsConstructor
public class FinancialInsightsSummaryController {
    private final FinancialInsightsSummaryService service;

    @GetMapping
    public ResponseEntity<FinancialInsightsSummaryResponse> getSummary(Authentication authentication,
            @RequestParam(required = false) AnalyticsService.Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.generateSummary(authentication, period, startDate, endDate));
    }
}
