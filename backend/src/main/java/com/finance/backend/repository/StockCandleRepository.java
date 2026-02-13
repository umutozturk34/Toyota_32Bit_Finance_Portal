package com.finance.backend.repository;

import com.finance.backend.model.StockCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockCandleRepository extends JpaRepository<StockCandle, Long> {
    
    List<StockCandle> findByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    
    List<StockCandle> findByStockSymbolOrderByCandleDateAsc(String stockSymbol);
    
    List<StockCandle> findByStockSymbolAndCandleDateBetweenOrderByCandleDateAsc(
        String stockSymbol,
        LocalDateTime startDate,
        LocalDateTime endDate
    );
    
    Optional<StockCandle> findFirstByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    
    Optional<StockCandle> findByStockSymbolAndCandleDate(String stockSymbol, LocalDateTime candleDate);
    
    List<StockCandle> findTop1825ByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    
    long countByStockSymbol(String stockSymbol);
    
    @Modifying
    void deleteByStockSymbolAndCandleDateBefore(String stockSymbol, LocalDateTime beforeDate);
    
    @Modifying
    void deleteByCandleDateBefore(LocalDateTime beforeDate);
}
