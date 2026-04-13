package com.finance.backend.repository;
import com.finance.backend.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockRepository extends JpaRepository<Stock, String>, JpaSpecificationExecutor<Stock> {
    @Query("SELECT s.symbol FROM Stock s")
    List<String> findAllSymbols();

    @Query("SELECT s.stockSegment, COUNT(s) FROM Stock s WHERE s.stockSegment IS NOT NULL GROUP BY s.stockSegment ORDER BY s.stockSegment")
    List<Object[]> countBySegment();
}
