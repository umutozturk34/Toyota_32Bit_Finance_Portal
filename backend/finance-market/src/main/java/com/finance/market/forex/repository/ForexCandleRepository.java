package com.finance.market.forex.repository;

import com.finance.market.forex.model.ForexCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence for daily forex candles, queried by currency and date for history, pricing, and change. */
public interface ForexCandleRepository extends JpaRepository<ForexCandle, Long> {

    List<ForexCandle> findByCurrencyCodeOrderByCandleDateAsc(String currencyCode);

    List<ForexCandle> findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(
            String currencyCode, LocalDateTime start, LocalDateTime end);

    Optional<ForexCandle> findFirstByCurrencyCodeOrderByCandleDateDesc(String currencyCode);

    Optional<ForexCandle> findFirstByCurrencyCodeAndSellingPriceGreaterThanOrderByCandleDateDesc(String currencyCode, BigDecimal sellingPrice);

    List<ForexCandle> findByCurrencyCodeAndCandleDateIn(String currencyCode, Collection<LocalDateTime> candleDates);

    List<ForexCandle> findTop2ByCurrencyCodeOrderByCandleDateDesc(String currencyCode);
    Optional<ForexCandle> findFirstByCurrencyCodeAndCandleDateBeforeOrderByCandleDateDesc(String currencyCode, LocalDateTime before);
}
