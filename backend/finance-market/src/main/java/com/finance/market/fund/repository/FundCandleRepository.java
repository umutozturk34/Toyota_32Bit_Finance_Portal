package com.finance.market.fund.repository;

import com.finance.market.fund.model.FundCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence access for {@link FundCandle} daily price time-series rows.
 *
 * <p>Backs fund history/charting and pricing: per-fund history retrieval,
 * coverage diagnostics, latest/as-of price lookups, incremental backfill
 * support, and retention pruning.
 */
public interface FundCandleRepository extends JpaRepository<FundCandle, Long> {

    /**
     * Reports the earliest and latest candle date held for every fund.
     *
     * <p>Used as a coverage/freshness diagnostic to decide which funds need
     * backfilling.
     *
     * @return rows of {@code [fundCode, min(candleDate), max(candleDate)]}
     */
    @Query("SELECT c.fundCode, MIN(c.candleDate), MAX(c.candleDate) FROM FundCandle c GROUP BY c.fundCode")
    List<Object[]> findCandleDateRangePerFund();

    /**
     * The previous close (second-most-recent candle price) for every fund in ONE query, so a batch
     * change-percent recompute resolves every fund's prior price at once instead of a per-fund
     * prior-candle lookup (an N+1 across ~800 funds). Funds with a single candle have no previous
     * close and are simply absent from the result.
     *
     * @return rows of {@code [fundCode, previousClosePrice]}
     */
    @Query(value = "SELECT fund_code, price FROM ("
            + "SELECT fund_code, price, ROW_NUMBER() OVER (PARTITION BY fund_code ORDER BY candle_date DESC) AS rn "
            + "FROM fund_candles) ranked WHERE ranked.rn = 2", nativeQuery = true)
    List<Object[]> findPreviousClosePricePerFund();

    /**
     * Reports the candle count held for every fund.
     *
     * @return rows of {@code [fundCode, Long count]}
     */
    @Query("SELECT c.fundCode, COUNT(c) FROM FundCandle c GROUP BY c.fundCode")
    List<Object[]> countCandlesPerFund();

    /**
     * Lists a fund's candle dates from a starting point onward, chronologically.
     *
     * <p>Used to compute which dates are missing within a refresh window.
     *
     * @param fundCode the fund code
     * @param from inclusive lower bound on candle date
     * @return candle dates at or after {@code from}, oldest-first
     */
    @Query("SELECT c.candleDate FROM FundCandle c WHERE c.fundCode = :fundCode AND c.candleDate >= :from ORDER BY c.candleDate ASC")
    List<LocalDateTime> findCandleDatesSince(@org.springframework.data.repository.query.Param("fundCode") String fundCode,
                                             @org.springframework.data.repository.query.Param("from") LocalDateTime from);

    /**
     * Returns the full candle history of a fund, newest-first.
     *
     * @param fundCode the fund code
     * @return candles in reverse-chronological order, empty if none exist
     */
    List<FundCandle> findByFundCodeOrderByCandleDateDesc(String fundCode);

    /**
     * Returns the full candle history of a fund, oldest-first.
     *
     * @param fundCode the fund code
     * @return candles in chronological order, empty if none exist
     */
    List<FundCandle> findByFundCodeOrderByCandleDateAsc(String fundCode);

    /**
     * Returns the candle history of a fund restricted to a date window.
     *
     * @param fundCode the fund code
     * @param startDate inclusive window start
     * @param endDate inclusive window end
     * @return candles within the window, oldest-first
     */
    List<FundCandle> findByFundCodeAndCandleDateBetweenOrderByCandleDateAsc(
        String fundCode,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    /**
     * Returns the most recent candle for a fund.
     *
     * @param fundCode the fund code
     * @return the latest candle, or empty if no history exists
     */
    Optional<FundCandle> findFirstByFundCodeOrderByCandleDateDesc(String fundCode);

    /**
     * Returns the most recent candle whose price exceeds the given threshold.
     *
     * <p>Used to skip zero/placeholder prices when sourcing a valid last price.
     *
     * @param fundCode the fund code
     * @param price exclusive lower bound the price must exceed
     * @return the latest qualifying candle, or empty if none
     */
    Optional<FundCandle> findFirstByFundCodeAndPriceGreaterThanOrderByCandleDateDesc(String fundCode, BigDecimal price);

    /**
     * Returns the latest candle strictly before the given date.
     *
     * <p>Used to resolve an as-of/prior price relative to a reference moment.
     *
     * @param fundCode the fund code
     * @param before exclusive upper bound on candle date
     * @return the closest preceding candle, or empty if none
     */
    Optional<FundCandle> findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(String fundCode, LocalDateTime before);

    /**
     * Looks up a single candle by fund and exact date.
     *
     * <p>Used during ingestion to detect/update an existing point for that date.
     *
     * @param fundCode the fund code
     * @param candleDate the exact candle date
     * @return the matching candle, or empty if absent
     */
    Optional<FundCandle> findByFundCodeAndCandleDate(String fundCode, LocalDateTime candleDate);

    /**
     * Fetches candles for a fund matching an explicit set of dates.
     *
     * <p>Typically used to determine which target dates are already persisted
     * before an incremental backfill.
     *
     * @param fundCode the fund code
     * @param candleDates the dates of interest
     * @return matching candles, in no guaranteed order
     */
    List<FundCandle> findByFundCodeAndCandleDateIn(String fundCode, Collection<LocalDateTime> candleDates);

    /**
     * Returns up to the most recent 1825 candles (roughly five years) for a fund,
     * newest-first.
     *
     * <p>Caps history loading to the supported analytics window.
     *
     * @param fundCode the fund code
     * @return up to 1825 candles, reverse-chronological
     */
    List<FundCandle> findTop1825ByFundCodeOrderByCandleDateDesc(String fundCode);

    /**
     * Counts the candles stored for a fund.
     *
     * @param fundCode the fund code
     * @return the persisted candle count
     */
    long countByFundCode(String fundCode);

    /**
     * Prunes a single fund's candles older than the cutoff for retention.
     *
     * @param fundCode the fund code
     * @param beforeDate exclusive upper bound; candles strictly before it are deleted
     */
    void deleteByFundCodeAndCandleDateBefore(String fundCode, LocalDateTime beforeDate);

    /**
     * Prunes candles older than the cutoff across all funds for retention.
     *
     * @param beforeDate exclusive upper bound; candles strictly before it are deleted
     */
    void deleteByCandleDateBefore(LocalDateTime beforeDate);
}
