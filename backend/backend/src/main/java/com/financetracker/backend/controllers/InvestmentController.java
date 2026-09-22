package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.InvestmentHoldingResponse;
import com.financetracker.backend.dto.InvestmentRequest;
import com.financetracker.backend.dto.InvestmentResponse;
import com.financetracker.backend.services.InvestmentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;

    @GetMapping
    public ResponseEntity<List<InvestmentResponse>> getInvestments(
            Authentication authentication,
            @RequestParam(required = false) String ticker
    ) {
        return ResponseEntity.ok(investmentService.getInvestments(authentication, ticker));
    }

    @GetMapping("/holdings")
    public ResponseEntity<List<InvestmentHoldingResponse>> getHoldings(Authentication authentication) {
        return ResponseEntity.ok(investmentService.getHoldings(authentication));
    }

    @PostMapping
    public ResponseEntity<InvestmentResponse> createInvestment(
            Authentication authentication,
            @Valid @RequestBody InvestmentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investmentService.createInvestment(authentication, request));
    }

    @PutMapping("/{investmentId}")
    public ResponseEntity<InvestmentResponse> updateInvestment(
            Authentication authentication,
            @PathVariable Long investmentId,
            @Valid @RequestBody InvestmentRequest request
    ) {
        return ResponseEntity.ok(investmentService.updateInvestment(authentication, investmentId, request));
    }

    @DeleteMapping("/{investmentId}")
    public ResponseEntity<Void> deleteInvestment(
            Authentication authentication,
            @PathVariable Long investmentId
    ) {
        investmentService.deleteInvestment(authentication, investmentId);
        return ResponseEntity.noContent().build();
    }
}
