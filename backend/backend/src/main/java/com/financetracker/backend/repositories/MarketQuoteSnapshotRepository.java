package com.financetracker.backend.repositories;

import com.financetracker.backend.entities.MarketQuoteSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketQuoteSnapshotRepository extends JpaRepository<MarketQuoteSnapshot, String> {
    List<MarketQuoteSnapshot> findAllBySymbolIn(Collection<String> symbols);
}
