package com.finance.backend.repository;

import com.finance.backend.entity.StockPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {
    
    @Query("SELECT s FROM StockPrice s WHERE s.timestamp = (SELECT MAX(s2.timestamp) FROM StockPrice s2 WHERE s2.symbol = s.symbol) ORDER BY s.symbol")
    Page<StockPrice> findLatestPrices(Pageable pageable);
    
    @Query("SELECT s FROM StockPrice s WHERE s.market = :market AND s.timestamp = (SELECT MAX(s2.timestamp) FROM StockPrice s2 WHERE s2.symbol = s.symbol) ORDER BY s.symbol")
    Page<StockPrice> findLatestPricesByMarket(@Param("market") String market, Pageable pageable);
    
    Optional<StockPrice> findFirstBySymbolOrderByTimestampDesc(String symbol);
    
    List<StockPrice> findBySymbolOrderByTimestampDesc(String symbol);
    
    long countByMarket(String market);
}
