package com.finance.market.stock.repository;
import com.finance.market.stock.model.StockCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
/**
 * Persistence access for historical OHLC price bars ({@link StockCandle}) keyed by stock symbol.
 * Provides chronological range reads, latest/previous lookups and time-based pruning used by the
 * pricing and history services.
 */
@Repository
public interface StockCandleRepository extends JpaRepository<StockCandle, Long> {
    /** Returns all candles for a symbol, newest first. */
    List<StockCandle> findByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    /** Returns all candles for a symbol in chronological order, ready to plot as a series. */
    List<StockCandle> findByStockSymbolOrderByCandleDateAsc(String stockSymbol);
    /**
     * Returns the candles for a symbol whose date falls within the inclusive window, in
     * chronological order.
     *
     * @param startDate inclusive lower bound of the candle date
     * @param endDate   inclusive upper bound of the candle date
     */
    List<StockCandle> findByStockSymbolAndCandleDateBetweenOrderByCandleDateAsc(
        String stockSymbol,
        LocalDateTime startDate,
        LocalDateTime endDate
    );
    /**
     * Returns the most recent candle for a symbol, i.e. its latest known price bar.
     *
     * @return the newest candle, or empty if none exist for the symbol
     */
    Optional<StockCandle> findFirstByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    /**
     * Returns the most recent candle for a symbol whose close exceeds the given threshold; used to
     * locate the latest bar that cleared a price level (e.g. all-time-high style scans).
     */
    Optional<StockCandle> findFirstByStockSymbolAndCloseGreaterThanOrderByCandleDateDesc(String stockSymbol, BigDecimal close);
    /**
     * Returns the two most recent candles for a symbol (newest first), the minimal set needed to
     * compute the latest day-over-day change.
     */
    List<StockCandle> findTop2ByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    /**
     * Returns the latest candle strictly before the given instant; used to find the prior reference
     * bar for a point-in-time comparison.
     */
    Optional<StockCandle> findFirstByStockSymbolAndCandleDateBeforeOrderByCandleDateDesc(String stockSymbol, LocalDateTime before);
    /** Returns the candle for a symbol at an exact date, if one was recorded. */
    Optional<StockCandle> findByStockSymbolAndCandleDate(String stockSymbol, LocalDateTime candleDate);

    /**
     * Returns the candles for a symbol matching any of the supplied dates; used to fetch values for
     * a discrete set of reference dates in a single round trip.
     */
    List<StockCandle> findByStockSymbolAndCandleDateIn(String stockSymbol, Collection<LocalDateTime> candleDates);

    /**
     * Returns up to the latest 1825 candles (~5 years of daily bars) for a symbol, newest first,
     * bounding the working set for long-horizon analytics.
     */
    List<StockCandle> findTop1825ByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    /** Returns how many candles are stored for the symbol, e.g. to assess history coverage. */
    long countByStockSymbol(String stockSymbol);
    /** Prunes a single symbol's candles older than the given cutoff. */
    void deleteByStockSymbolAndCandleDateBefore(String stockSymbol, LocalDateTime beforeDate);
    /** Prunes candles across all symbols older than the given cutoff (retention housekeeping). */
    void deleteByCandleDateBefore(LocalDateTime beforeDate);
}
