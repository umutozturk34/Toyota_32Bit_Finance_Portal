package com.finance.backend.repository;
import com.finance.backend.model.ForexCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
@Repository
public interface ForexCandleRepository extends JpaRepository<ForexCandle, Long> {
    List<ForexCandle> findByCurrencyCodeOrderByCandleDateAsc(String currencyCode);
    List<ForexCandle> findByCurrencyCodeOrderByCandleDateDesc(String currencyCode);
    List<ForexCandle> findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(
        String currencyCode, LocalDateTime start, LocalDateTime end
    );
    Optional<ForexCandle> findFirstByCurrencyCodeOrderByCandleDateDesc(String currencyCode);
    Optional<ForexCandle> findByCurrencyCodeAndCandleDate(String currencyCode, LocalDateTime candleDate);

    List<ForexCandle> findByCurrencyCodeAndCandleDateIn(String currencyCode, Collection<LocalDateTime> candleDates);

    Long countByCurrencyCode(String currencyCode);

    List<ForexCandle> findTop2ByCurrencyCodeOrderByCandleDateDesc(String currencyCode);
}