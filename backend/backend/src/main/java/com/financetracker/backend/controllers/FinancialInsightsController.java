package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.FinancialInsightsResponse;
import com.financetracker.backend.services.FinancialInsightsService;
import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import com.financetracker.backend.services.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/financial-insights")
@RequiredArgsConstructor
public class FinancialInsightsController {
    private final FinancialInsightsService service;

    @GetMapping
    public ResponseEntity<FinancialInsightsResponse> getInsights(Authentication authentication,
            @RequestParam(required = false) AnalyticsService.Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(service.generateInsightsForUser(authentication, period, startDate, endDate));
    }
}
