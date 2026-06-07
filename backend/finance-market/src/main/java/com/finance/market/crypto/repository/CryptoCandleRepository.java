package com.finance.market.crypto.repository;
import com.finance.market.crypto.model.CryptoCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * Persistence access for {@link CryptoCandle} OHLC time-series rows.
 *
 * <p>Supports per-asset history retrieval, latest-point and as-of lookups,
 * existence/count checks, incremental upsert support, and retention pruning.
 */
@Repository
public interface CryptoCandleRepository extends JpaRepository<CryptoCandle, Long> {
    /**
     * Returns the full candle history of a crypto asset, oldest-first.
     *
     * @param cryptoId the crypto asset identifier
     * @return candles in chronological order, empty if none exist
     */
    List<CryptoCandle> findByCryptoIdOrderByCandleDateAsc(String cryptoId);
    /**
     * Returns the most recent candle for a crypto asset.
     *
     * @param cryptoId the crypto asset identifier
     * @return the latest candle, or empty if no history exists
     */
    Optional<CryptoCandle> findFirstByCryptoIdOrderByCandleDateDesc(String cryptoId);
    /**
     * Returns the most recent candle whose close exceeds the given threshold.
     *
     * <p>Used to skip zero/placeholder closes when sourcing a valid last price.
     *
     * @param cryptoId the crypto asset identifier
     * @param close exclusive lower bound the close must exceed
     * @return the latest qualifying candle, or empty if none
     */
    Optional<CryptoCandle> findFirstByCryptoIdAndCloseGreaterThanOrderByCandleDateDesc(String cryptoId, BigDecimal close);
    /**
     * Indicates whether any candle exists for the given crypto asset.
     *
     * @param cryptoId the crypto asset identifier
     * @return {@code true} if at least one candle is stored
     */
    boolean existsByCryptoId(String cryptoId);
    /**
     * Counts the candles stored for a crypto asset.
     *
     * @param cryptoId the crypto asset identifier
     * @return the persisted candle count
     */
    long countByCryptoId(String cryptoId);
    /**
     * Deletes the entire candle history of a crypto asset.
     *
     * @param cryptoId the crypto asset identifier
     */
    void deleteByCryptoId(String cryptoId);
    /**
     * Prunes candles older than the cutoff across all assets for retention.
     *
     * @param date exclusive upper bound; candles strictly before it are deleted
     */
    void deleteByCandleDateBefore(LocalDateTime date);
    /**
     * Looks up a single candle by asset and exact date.
     *
     * <p>Used during ingestion to detect/update an existing point for that date.
     *
     * @param cryptoId the crypto asset identifier
     * @param candleDate the exact candle date
     * @return the matching candle, or empty if absent
     */
    Optional<CryptoCandle> findByCryptoIdAndCandleDate(String cryptoId, LocalDateTime candleDate);
    /**
     * Fetches candles for an asset matching an explicit set of dates.
     *
     * <p>Typically used to determine which target dates are already persisted
     * before an incremental backfill.
     *
     * @param cryptoId the crypto asset identifier
     * @param candleDates the dates of interest
     * @return matching candles, in no guaranteed order
     */
    List<CryptoCandle> findByCryptoIdAndCandleDateIn(String cryptoId, List<LocalDateTime> candleDates);

    /**
     * Returns the candle history of an asset restricted to a date window.
     *
     * @param cryptoId the crypto asset identifier
     * @param startDate inclusive window start
     * @param endDate inclusive window end
     * @return candles within the window, oldest-first
     */
    List<CryptoCandle> findByCryptoIdAndCandleDateBetweenOrderByCandleDateAsc(
            String cryptoId, LocalDateTime startDate, LocalDateTime endDate);
}