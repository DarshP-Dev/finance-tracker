package com.financetracker.backend.services;

import com.financetracker.backend.dto.*;
import com.financetracker.backend.entities.Notification;
import com.financetracker.backend.entities.User;
import com.financetracker.backend.repositories.NotificationRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repository;
    private final AuthenticatedUserService authenticatedUserService;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(Authentication authentication, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification page or size");
        Long userId = authenticatedUserService.getCurrentUser(authentication).getId();
        var slice = repository.findByUserId(userId, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new NotificationPageResponse(slice.stream().map(this::response).toList(), page, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Authentication authentication) {
        return repository.countByUserIdAndReadFalse(authenticatedUserService.getCurrentUser(authentication).getId());
    }

    @Transactional
    public NotificationResponse markRead(Authentication authentication, Long id) {
        var notification = owned(authentication, id);
        notification.setRead(true);
        return response(notification);
    }

    @Transactional
    public void markAllRead(Authentication authentication) {
        repository.markAllRead(authenticatedUserService.getCurrentUser(authentication).getId());
    }

    @Transactional
    public void delete(Authentication authentication, Long id) { repository.delete(owned(authentication, id)); }

    /** Called by internal event rules in an independent transaction, never by a public create endpoint.
     * The PostgreSQL receipt insert is atomic across threads/instances and rolls back with insertion failures. */
    @Transactional
    public void create(User user, String key, Notification.Type type, Notification.Severity severity,
            String title, String message, String targetPath, Long relatedId) {
        int claimed = jdbc.update("insert into notification_receipts (user_id, reference_key) values (?, ?) "
                + "on conflict (user_id, reference_key) do nothing", user.getId(), key);
        if (claimed == 0) return;
        repository.saveAndFlush(Notification.builder().user(user).type(type).severity(severity)
                .title(title).message(message).targetPath(targetPath).relatedEntityId(relatedId)
                .createdAt(clock.instant()).build());
    }

    private Notification owned(Authentication authentication, Long id) {
        return repository.findByIdAndUserId(id, authenticatedUserService.getCurrentUser(authentication).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }
    private NotificationResponse response(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getSeverity(), n.getTitle(), n.getMessage(),
                n.isRead(), n.getCreatedAt(), n.getTargetPath(), n.getRelatedEntityId());
    }
}
