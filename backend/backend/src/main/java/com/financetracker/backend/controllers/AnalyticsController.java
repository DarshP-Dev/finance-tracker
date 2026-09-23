package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.AnalyticsResponse;
import com.financetracker.backend.services.AnalyticsService;
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

    @GetMapping
    public ResponseEntity<AnalyticsResponse> getAnalytics(Authentication authentication,
            @RequestParam(defaultValue = "THIS_MONTH") AnalyticsService.Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(analyticsService.getAnalytics(authentication, period, startDate, endDate));
    }
}
