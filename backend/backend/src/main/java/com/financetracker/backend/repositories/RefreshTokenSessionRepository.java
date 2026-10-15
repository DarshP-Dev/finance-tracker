package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.RefreshTokenSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {
    // Scalar lookup avoids caching an entity before acquiring the stable owner lock.
    @Query("select s.userId from RefreshTokenSession s where s.tokenHash = :hash")
    Optional<Long> findOwner(@Param("hash") String hash);
    Optional<RefreshTokenSession> findByTokenHash(String hash);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenSession s set s.revokedAt = :now where s.familyId = :family and s.revokedAt is null")
    int revokeFamily(@Param("family") UUID family, @Param("now") Instant now);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenSession s set s.revokedAt = :now where s.userId = :owner and s.revokedAt is null")
    int revokeUser(@Param("owner") Long owner, @Param("now") Instant now);
}
