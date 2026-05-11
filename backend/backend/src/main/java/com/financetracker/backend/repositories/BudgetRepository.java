package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.Budget;
import com.financetracker.backend.entities.TransactionCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdOrderByMonthDesc(Long userId);

    List<Budget> findByUserIdAndMonthOrderByCategoryAsc(Long userId, LocalDate month);

    Optional<Budget> findByUserIdAndCategoryAndMonth(
            Long userId,
            TransactionCategory category,
            LocalDate month
    );
}
