package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.FinancialInsightsResponse;
import com.financetracker.backend.services.FinancialInsightsService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<FinancialInsightsResponse> getInsights(Authentication authentication) {
        return ResponseEntity.ok(service.generateInsightsForUser(authentication));
    }
}
