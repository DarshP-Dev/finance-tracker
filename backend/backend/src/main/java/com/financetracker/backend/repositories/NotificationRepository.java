package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.Notification;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Slice<Notification> findByUserId(Long userId, Pageable pageable);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
    long countByUserIdAndReadFalse(Long userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllRead(Long userId);
}
