package com.finance.market.bond.repository;

import com.finance.market.bond.model.BondRateHistory;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for a bond's daily rate history, keyed by ISIN. Supports full chronological retrieval,
 * latest-observation lookup, and the existence/count checks used to drive incremental backfills.
 */
@Repository
public interface BondRateHistoryRepository extends JpaRepository<BondRateHistory, Long> {

    /** Returns the bond's full rate history in chronological (oldest-first) order, for charting. */
    List<BondRateHistory> findByIsinCodeOrderByRateDateAsc(String isinCode);

    /** Returns the most recent rate observation for the bond, if any. */
    Optional<BondRateHistory> findTopByIsinCodeOrderByRateDateDesc(String isinCode);

    /** Counts stored observations for the bond, used to decide between full seed and incremental update. */
    long countByIsinCode(String isinCode);

    /** Tests whether an observation already exists for the given bond and date, guarding against duplicate inserts. */
    boolean existsByIsinCodeAndRateDate(String isinCode, LocalDate rateDate);

    /**
     * Returns observations strictly after the given date in chronological order, used to fetch only
     * the new tail when incrementally extending an existing history.
     */
    List<BondRateHistory> findByIsinCodeAndRateDateAfterOrderByRateDateAsc(
            String isinCode, LocalDate afterDate);
}
