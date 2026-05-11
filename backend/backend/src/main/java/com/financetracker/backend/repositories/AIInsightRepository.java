package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.AIInsight;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIInsightRepository extends JpaRepository<AIInsight, Long> {

    List<AIInsight> findByUserIdOrderByGeneratedAtDesc(Long userId);
}
