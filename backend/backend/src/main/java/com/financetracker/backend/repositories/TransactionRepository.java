package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.Transaction;
import com.financetracker.backend.entities.TransactionCategory;
import com.financetracker.backend.entities.TransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

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

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    List<Transaction> findTop5ByUserIdOrderByDateDescIdDesc(Long userId);

    List<Transaction> findTop5ByUserIdAndDateBetweenOrderByDateDescIdDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId and t.type = :type
            """)
    java.math.BigDecimal sumAmountByUserIdAndType(
            @Param("userId") Long userId,
            @Param("type") TransactionType type
    );

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId
                and t.type = :type
                and t.date between :startDate and :endDate
            """)
    java.math.BigDecimal sumAmountByUserIdAndTypeBetweenDates(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId
                and t.type = com.financetracker.backend.entities.TransactionType.EXPENSE
                and t.category = :category
                and t.date between :startDate and :endDate
            """)
    java.math.BigDecimal sumExpenseAmountByUserIdAndCategoryBetweenDates(
            @Param("userId") Long userId,
            @Param("category") TransactionCategory category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select t.category, coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId and t.type = com.financetracker.backend.entities.TransactionType.EXPENSE
            group by t.category
            order by coalesce(sum(t.amount), 0) desc
            """)
    List<Object[]> sumExpensesByCategory(@Param("userId") Long userId);

    @Query("""
            select t.category, coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId
                and t.type = com.financetracker.backend.entities.TransactionType.EXPENSE
                and t.date between :startDate and :endDate
            group by t.category
            order by coalesce(sum(t.amount), 0) desc
            """)
    List<Object[]> sumExpensesByCategoryBetweenDates(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(
            value = """
                    select date_trunc('month', transaction_date)::date as month_start,
                           coalesce(sum(amount), 0) as total
                    from transactions
                    where user_id = :userId and type = 'EXPENSE'
                    group by month_start
                    order by month_start
                    """,
            nativeQuery = true
    )
    List<Object[]> sumMonthlyExpenses(@Param("userId") Long userId);

    @Query(
            value = """
                    select transaction_date::date as spending_date,
                           coalesce(sum(amount), 0) as total
                    from transactions
                    where user_id = :userId
                        and type = 'EXPENSE'
                        and transaction_date between :startDate and :endDate
                    group by spending_date
                    order by spending_date
                    """,
            nativeQuery = true
    )
    List<Object[]> sumDailyExpensesBetweenDates(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(
            value = """
                    select transaction_date::date as transaction_day,
                           type,
                           coalesce(sum(amount), 0) as total
                    from transactions
                    where user_id = :userId
                        and transaction_date between :startDate and :endDate
                    group by transaction_day, type
                    order by transaction_day
                    """,
            nativeQuery = true
    )
    List<Object[]> sumDailyCashFlowBetweenDates(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select t.type, coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId
            group by t.type
            """)
    List<Object[]> sumAmountByType(@Param("userId") Long userId);

    @Query("""
            select t.type, coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user.id = :userId
                and t.date between :startDate and :endDate
            group by t.type
            """)
    List<Object[]> sumAmountByTypeBetweenDates(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
