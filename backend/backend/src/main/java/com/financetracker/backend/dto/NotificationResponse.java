package com.financetracker.backend.dto;

import com.financetracker.backend.entities.Notification;
import java.time.Instant;

public record NotificationResponse(Long id, Notification.Type type, Notification.Severity severity,
        String title, String message, boolean read, Instant createdAt, String targetPath, Long relatedEntityId) {}
