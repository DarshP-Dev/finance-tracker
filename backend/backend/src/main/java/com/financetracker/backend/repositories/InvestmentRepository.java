package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.Investment;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    List<Investment> findByUserIdOrderByPurchaseDateDesc(Long userId);

    List<Investment> findByUserIdAndTickerOrderByPurchaseDateDesc(Long userId, String ticker);

    @Query("""
            select coalesce(sum(i.shares * i.purchasePrice), 0)
            from Investment i
            where i.user.id = :userId
            """)
    BigDecimal calculateInvestmentValue(@Param("userId") Long userId);
}
