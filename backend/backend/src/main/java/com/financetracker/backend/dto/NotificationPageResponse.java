package com.financetracker.backend.dto;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> notifications, int page, boolean hasMore) {}
