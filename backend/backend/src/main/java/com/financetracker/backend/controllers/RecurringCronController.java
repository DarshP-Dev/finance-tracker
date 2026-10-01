package com.financetracker.backend.controllers;

import com.financetracker.backend.services.RecurringTransactionProcessor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecurringCronController {

    public static final String PATH = "/api/internal/recurring/process-due";

    private final RecurringTransactionProcessor processor;
    private final String cronSecret;

    public RecurringCronController(
            RecurringTransactionProcessor processor,
            @Value("${CRON_SECRET:}") String cronSecret
    ) {
        this.processor = processor;
        this.cronSecret = cronSecret;
    }

    @GetMapping(PATH)
    public ResponseEntity<RecurringTransactionProcessor.ProcessingResult> processDue(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        if (cronSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        String expected = "Bearer " + cronSecret;
        if (authorization == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RecurringTransactionProcessor.ProcessingResult result = processor.processDueRecurringTransactions();
        return ResponseEntity.status(result.failedDefinitions() > 0 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK)
                .body(result);
    }
}
