package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.RecurringTransaction;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    List<RecurringTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<RecurringTransaction> findByIdAndUserId(Long id, Long userId);

    @Query("select r from RecurringTransaction r where r.user.id = :userId and r.active = true "
            + "and r.nextOccurrence <= :to and (r.endDate is null or r.endDate >= :from)")
    List<RecurringTransaction> findForecastCandidates(
            @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select r.id from RecurringTransaction r where r.active = true and r.nextOccurrence <= :today "
            + "and r.id > :afterId order by r.id")
    List<Long> findDueIds(@Param("today") LocalDate today, @Param("afterId") Long afterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringTransaction r where r.id = :id")
    Optional<RecurringTransaction> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringTransaction r where r.id = :id and r.user.id = :userId")
    Optional<RecurringTransaction> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
