package com.finance.backend.repository;

import com.finance.backend.model.CryptoCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Crypto candle/chart data
 */
@Repository
public interface CryptoCandleRepository extends JpaRepository<CryptoCandle, Long> {
    
    /**
     * Find all candles for a specific crypto, ordered by date ascending
     * @param cryptoId The crypto ID (e.g., "bitcoin")
     * @return List of candles sorted by date
     */
    List<CryptoCandle> findByCryptoIdOrderByCandleDateAsc(String cryptoId);
    
    /**
     * Check if any candle data exists for a specific crypto
     * Used for smart fetching (365 days vs 1 day)
     * @param cryptoId The crypto ID (e.g., "bitcoin")
     * @return true if data exists, false if first time
     */
    boolean existsByCryptoId(String cryptoId);
    
    /**
     * Count total candles for a specific crypto
     * Used for self-healing mechanism (detect gaps)
     * @param cryptoId The crypto ID (e.g., "bitcoin")
     * @return Number of candles in database
     */
    long countByCryptoId(String cryptoId);
    
    /**
     * Delete all candles for a specific crypto
     * Used for self-healing (wipe bad data before refetch)
     * @param cryptoId The crypto ID (e.g., "bitcoin")
     */
    void deleteByCryptoId(String cryptoId);
    
    /**
     * Delete all candles older than specified date
     * Used for pruning old data (keep only 365 days)
     * @param date Cutoff date (e.g., now - 365 days)
     */
    void deleteByCandleDateBefore(LocalDateTime date);
}
