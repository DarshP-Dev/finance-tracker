package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.RecurringTransactionRequest;
import com.financetracker.backend.dto.RecurringTransactionResponse;
import com.financetracker.backend.dto.RecurringForecastResponse;
import com.financetracker.backend.dto.TransactionResponse;
import com.financetracker.backend.dto.UpcomingRecurringTransactionResponse;
import com.financetracker.backend.services.RecurringForecastService;
import com.financetracker.backend.services.RecurringTransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recurring-transactions")
@RequiredArgsConstructor
public class RecurringTransactionController {

    private final RecurringTransactionService recurringTransactionService;
    private final RecurringForecastService recurringForecastService;

    @GetMapping("/upcoming")
    public ResponseEntity<List<UpcomingRecurringTransactionResponse>> upcoming(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(recurringForecastService.getUpcoming(authentication, from, to));
    }

    @GetMapping("/forecast")
    public ResponseEntity<RecurringForecastResponse> forecast(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(recurringForecastService.getForecast(authentication, from, to));
    }

    @PostMapping
    public ResponseEntity<RecurringTransactionResponse> create(
            Authentication authentication, @Valid @RequestBody RecurringTransactionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recurringTransactionService.createRecurringTransaction(authentication, request));
    }

    @GetMapping
    public ResponseEntity<List<RecurringTransactionResponse>> getAll(Authentication authentication) {
        return ResponseEntity.ok(recurringTransactionService.getRecurringTransactions(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> getById(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(recurringTransactionService.getRecurringTransactionById(authentication, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> update(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody RecurringTransactionRequest request
    ) {
        return ResponseEntity.ok(recurringTransactionService.updateRecurringTransaction(authentication, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        recurringTransactionService.deleteRecurringTransaction(authentication, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/pause")
    public ResponseEntity<RecurringTransactionResponse> pause(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(recurringTransactionService.pauseRecurringTransaction(authentication, id));
    }

    @PatchMapping("/{id}/resume")
    public ResponseEntity<RecurringTransactionResponse> resume(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(recurringTransactionService.resumeRecurringTransaction(authentication, id));
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<TransactionResponse> generate(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recurringTransactionService.generateNextTransaction(authentication, id));
    }
}
