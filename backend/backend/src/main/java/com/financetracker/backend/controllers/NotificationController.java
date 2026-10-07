package com.financetracker.backend.controllers;

import com.financetracker.backend.dto.*;
import com.financetracker.backend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;
    public record UnreadCountResponse(long count) {}

    @GetMapping
    public ResponseEntity<NotificationPageResponse> get(Authentication authentication,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.getNotifications(authentication, page, size));
    }
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> count(Authentication authentication) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new UnreadCountResponse(service.getUnreadCount(authentication)));
    }
    @PatchMapping("/{id}/read")
    public NotificationResponse read(Authentication authentication, @PathVariable Long id) { return service.markRead(authentication, id); }
    @PatchMapping("/read-all")
    public ResponseEntity<Void> readAll(Authentication authentication) {
        service.markAllRead(authentication); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        service.delete(authentication, id); return ResponseEntity.noContent().build();
    }
}
