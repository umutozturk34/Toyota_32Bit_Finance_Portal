package com.finance.market.macro.repository;

import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence access for the individual time-series observations ({@link MacroIndicatorPoint})
 * that make up a macroeconomic {@link MacroIndicator}.
 */
public interface MacroIndicatorPointRepository extends JpaRepository<MacroIndicatorPoint, Long> {

    /**
     * Returns the most recent observation recorded for the given indicator, i.e. its latest value.
     *
     * @return the newest point, or empty if the indicator has no observations yet
     */
    Optional<MacroIndicatorPoint> findFirstByIndicatorOrderByObservedAtDesc(MacroIndicator indicator);

    /**
     * Loads the indicator's observations within an inclusive observation-date window, ordered
     * chronologically so the result is ready to render as a time series.
     *
     * @param from inclusive lower bound of the observation date
     * @param to   inclusive upper bound of the observation date
     */
    List<MacroIndicatorPoint> findByIndicatorAndObservedAtBetweenOrderByObservedAtAsc(
            MacroIndicator indicator, LocalDate from, LocalDate to);

    /**
     * Tests whether an observation already exists for the indicator on the given date; used to
     * avoid inserting duplicate points during ingestion.
     */
    boolean existsByIndicatorAndObservedAt(MacroIndicator indicator, LocalDate observedAt);
}
