package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.Transaction;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByDateDesc(Long userId);

    List<Transaction> findByUserIdAndDateBetweenOrderByDateDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<Transaction> findByUserIdAndCategoryAndDateBetweenOrderByDateDesc(
            Long userId,
            TransactionCategory category,
            LocalDate startDate,
            LocalDate endDate
    );

    List<Transaction> findByUserIdAndTypeOrderByDateDesc(Long userId, TransactionType type);
}
