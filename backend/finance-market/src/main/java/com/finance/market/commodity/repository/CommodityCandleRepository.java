package com.finance.market.commodity.repository;

import com.finance.market.commodity.model.CommodityCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence access for {@link CommodityCandle} OHLC time-series rows.
 *
 * <p>Provides per-commodity history retrieval, latest-point lookups, gap-filling
 * by explicit dates, and retention pruning.
 */
public interface CommodityCandleRepository extends JpaRepository<CommodityCandle, Long> {

    /**
     * Returns the full candle history of a commodity in chronological order.
     *
     * @param commodityCode the commodity identifier
     * @return candles oldest-first, empty if none exist
     */
    List<CommodityCandle> findByCommodityCodeOrderByCandleDateAsc(String commodityCode);

    /**
     * Returns the candle history of a commodity restricted to a date window.
     *
     * @param commodityCode the commodity identifier
     * @param start inclusive window start
     * @param end   inclusive window end
     * @return candles within the window, oldest-first
     */
    List<CommodityCandle> findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
            String commodityCode, LocalDateTime start, LocalDateTime end);

    /**
     * Returns the most recent candle for a commodity.
     *
     * @param commodityCode the commodity identifier
     * @return the latest candle, or empty if the commodity has no history
     */
    Optional<CommodityCandle> findFirstByCommodityCodeOrderByCandleDateDesc(String commodityCode);

    /**
     * Returns the most recent candle whose close exceeds the given threshold.
     *
     * <p>Used to skip placeholder/zero closes when sourcing a valid last price.
     *
     * @param commodityCode the commodity identifier
     * @param close exclusive lower bound the close must exceed
     * @return the latest qualifying candle, or empty if none
     */
    Optional<CommodityCandle> findFirstByCommodityCodeAndCloseGreaterThanOrderByCandleDateDesc(String commodityCode, BigDecimal close);

    /**
     * Fetches candles for a commodity matching an explicit set of dates.
     *
     * <p>Typically used to detect which target dates are already persisted before
     * an incremental backfill.
     *
     * @param commodityCode the commodity identifier
     * @param candleDates the dates of interest
     * @return matching candles, in no guaranteed order
     */
    List<CommodityCandle> findByCommodityCodeAndCandleDateIn(String commodityCode, Collection<LocalDateTime> candleDates);

    /**
     * Counts how many candles are stored for a commodity.
     *
     * @param commodityCode the commodity identifier
     * @return the persisted candle count
     */
    Long countByCommodityCode(String commodityCode);

    /**
     * Prunes candles older than the cutoff across all commodities for retention.
     *
     * @param cutoffDate exclusive upper bound; candles strictly before it are deleted
     * @return number of rows deleted
     */
    int deleteByCandleDateBefore(LocalDateTime cutoffDate);

    /**
     * Returns the two most recent candles for a commodity.
     *
     * <p>Supports day-over-day change computation (latest versus previous).
     *
     * @param commodityCode the commodity identifier
     * @return up to two candles, newest-first
     */
    List<CommodityCandle> findTop2ByCommodityCodeOrderByCandleDateDesc(String commodityCode);

    /**
     * Returns the latest candle strictly before the given date.
     *
     * <p>Used to resolve an as-of/prior price relative to a reference moment.
     *
     * @param commodityCode the commodity identifier
     * @param before exclusive upper bound on candle date
     * @return the closest preceding candle, or empty if none
     */
    Optional<CommodityCandle> findFirstByCommodityCodeAndCandleDateBeforeOrderByCandleDateDesc(String commodityCode, LocalDateTime before);
}
