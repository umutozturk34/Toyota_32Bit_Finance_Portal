package com.finance.market.viop.repository;

import com.finance.market.viop.model.ViopCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence for VIOP daily candles, keyed and queried by contract symbol and date. */
public interface ViopCandleRepository extends JpaRepository<ViopCandle, Long> {

    List<ViopCandle> findBySymbolOrderByCandleDateAsc(String symbol);

    List<ViopCandle> findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
            String symbol, LocalDateTime start, LocalDateTime end);

    Optional<ViopCandle> findFirstBySymbolOrderByCandleDateDesc(String symbol);

    Optional<ViopCandle> findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc(String symbol, BigDecimal close);

    Optional<ViopCandle> findFirstBySymbolAndCandleDateLessThanEqualOrderByCandleDateDesc(
            String symbol, LocalDateTime cutoff);

    List<ViopCandle> findBySymbolAndCandleDateIn(String symbol, Collection<LocalDateTime> candleDates);

    long countBySymbol(String symbol);
}
