package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.AnalyticsResponse;
import com.financetracker.backend.services.AnalyticsService;
import com.financetracker.backend.services.InvestmentPortfolioService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    private final InvestmentPortfolioService portfolioService;

    @GetMapping
    public ResponseEntity<AnalyticsResponse> getAnalytics(Authentication authentication,
            @RequestParam(defaultValue = "THIS_MONTH") AnalyticsService.Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        var report = analyticsService.getAnalytics(authentication, period, startDate, endDate);
        return ResponseEntity.ok(report.withPortfolio(portfolioService.getPortfolio(authentication)));
    }
}
