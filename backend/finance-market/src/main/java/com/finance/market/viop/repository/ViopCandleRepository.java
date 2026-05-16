package com.finance.market.viop.repository;

import com.finance.market.viop.model.ViopCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ViopCandleRepository extends JpaRepository<ViopCandle, Long> {

    List<ViopCandle> findBySymbolOrderByCandleDateAsc(String symbol);

    List<ViopCandle> findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
            String symbol, LocalDateTime start, LocalDateTime end);

    Optional<ViopCandle> findFirstBySymbolOrderByCandleDateDesc(String symbol);

    List<ViopCandle> findBySymbolAndCandleDateIn(String symbol, Collection<LocalDateTime> candleDates);

    long countBySymbol(String symbol);
}
