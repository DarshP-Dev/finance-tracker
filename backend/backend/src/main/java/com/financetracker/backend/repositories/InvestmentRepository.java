package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.Investment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    List<Investment> findByUserIdOrderByPurchaseDateDesc(Long userId);

    List<Investment> findByUserIdAndTickerOrderByPurchaseDateDesc(Long userId, String ticker);

    Optional<Investment> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select coalesce(sum(i.shares * i.purchasePrice), 0)
            from Investment i
            where i.user.id = :userId
            """)
    BigDecimal calculateInvestmentValue(@Param("userId") Long userId);

    @Query("""
            select coalesce(sum(i.shares * i.purchasePrice), 0)
            from Investment i
            where i.user.id = :userId and i.purchaseDate between :startDate and :endDate
            """)
    BigDecimal sumCostBetweenDates(@Param("userId") Long userId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query(value = """
            select date_trunc('month', purchase_date)::date, sum(shares * purchase_price)
            from investments
            where user_id = :userId and purchase_date between :startDate and :endDate
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> sumMonthlyCostBetweenDates(@Param("userId") Long userId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("""
            select i.ticker, sum(i.shares * i.purchasePrice)
            from Investment i
            where i.user.id = :userId
            group by i.ticker
            order by sum(i.shares * i.purchasePrice) desc
            """)
    List<Object[]> sumCostByTicker(@Param("userId") Long userId);
}
