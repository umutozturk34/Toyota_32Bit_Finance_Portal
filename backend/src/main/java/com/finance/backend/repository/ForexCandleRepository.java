package com.finance.backend.repository;

import com.finance.backend.model.ForexCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ForexCandleRepository extends JpaRepository<ForexCandle, Long> {
    
    List<ForexCandle> findByCurrencyCodeOrderByCandleDateDesc(String currencyCode);
    
    List<ForexCandle> findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(
        String currencyCode, LocalDateTime start, LocalDateTime end
    );
    
    Optional<ForexCandle> findByCurrencyCodeAndCandleDate(String currencyCode, LocalDateTime candleDate);
    
    List<ForexCandle> findTop1825ByCurrencyCodeOrderByCandleDateDesc(String currencyCode);
    
    List<ForexCandle> findTop1825ByCurrencyCodeOrderByCandleDateAsc(String currencyCode);
    
    Long countByCurrencyCode(String currencyCode);
    
    @Modifying
    @Transactional
    int deleteByCandleDateBefore(LocalDateTime cutoffDate);
    
    @Modifying
    @Transactional
    int deleteByCurrencyCodeAndCandleDateBefore(String currencyCode, LocalDateTime cutoffDate);
}
