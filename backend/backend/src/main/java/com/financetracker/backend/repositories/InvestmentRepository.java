package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.Investment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    List<Investment> findByUserIdOrderByPurchaseDateDesc(Long userId);

    List<Investment> findByUserIdAndTickerOrderByPurchaseDateDesc(Long userId, String ticker);
}
