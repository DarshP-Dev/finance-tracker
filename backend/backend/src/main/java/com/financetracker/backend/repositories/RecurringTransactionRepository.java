package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.RecurringTransaction;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    List<RecurringTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<RecurringTransaction> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringTransaction r where r.id = :id and r.user.id = :userId")
    Optional<RecurringTransaction> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
