package com.financetracker.backend.dto;

public record PortfolioRefreshResponse(PortfolioResponse portfolio, Status status, int retryAfterSeconds) {
    public enum Status { ACCEPTED, COOLDOWN }
}
