package com.finance.market.macro.repository;

import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence access for macroeconomic indicator definitions ({@link MacroIndicator}),
 * the catalog entries that each own a series of observation points.
 */
public interface MacroIndicatorRepository extends JpaRepository<MacroIndicator, Long> {

    /**
     * Looks up an indicator by its unique business code (e.g. the upstream provider's series key).
     *
     * @return the matching indicator, or empty if no indicator has that code
     */
    Optional<MacroIndicator> findByCode(String code);

    /** Returns all indicators belonging to the given macroeconomic category. */
    List<MacroIndicator> findByCategory(MacroCategory category);

    /**
     * Returns the indicators flagged as prominent (the highlighted/featured subset),
     * grouped by category for ordered display.
     */
    List<MacroIndicator> findByProminentTrueOrderByCategoryAsc();
}
