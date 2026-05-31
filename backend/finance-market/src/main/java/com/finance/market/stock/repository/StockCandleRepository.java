package com.finance.market.stock.repository;
import com.finance.market.stock.model.StockCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
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
    Optional<StockCandle> findFirstByStockSymbolAndCloseGreaterThanOrderByCandleDateDesc(String stockSymbol, BigDecimal close);
    List<StockCandle> findTop2ByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    Optional<StockCandle> findFirstByStockSymbolAndCandleDateBeforeOrderByCandleDateDesc(String stockSymbol, LocalDateTime before);
    Optional<StockCandle> findByStockSymbolAndCandleDate(String stockSymbol, LocalDateTime candleDate);

    List<StockCandle> findByStockSymbolAndCandleDateIn(String stockSymbol, Collection<LocalDateTime> candleDates);

    List<StockCandle> findTop1825ByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    long countByStockSymbol(String stockSymbol);
    void deleteByStockSymbolAndCandleDateBefore(String stockSymbol, LocalDateTime beforeDate);
    void deleteByCandleDateBefore(LocalDateTime beforeDate);
}
