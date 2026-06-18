package com.finance.market.fund.repository;

import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persistence access for {@link Fund} entities, keyed by fund code.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic, filterable
 * fund listing/search queries.
 */
public interface FundRepository extends JpaRepository<Fund, String>, JpaSpecificationExecutor<Fund> {
    /**
     * Returns all funds belonging to the given category.
     *
     * @param fundType the fund category to filter by
     * @return funds of the requested type, empty if none match
     */
    List<Fund> findByFundType(FundType fundType);

    /**
     * Projects only the fund codes (primary keys) of all funds.
     *
     * <p>Lightweight lookup used to drive batch refresh/iteration without
     * loading full entities.
     *
     * @return every persisted fund code
     */
    @Query("SELECT f.fundCode FROM Fund f")
    List<String> findAllFundCodes();

    /**
     * Every fund's code paired with its long name, for news mention-matching (a fund can be named either by its
     * parenthesised code or, more rarely, by its full title). Projection-only so the catalog build stays cheap.
     *
     * @return rows of {@code [fundCode, name]}
     */
    @Query("SELECT f.fundCode, f.name FROM Fund f")
    List<Object[]> findAllFundCodesAndNames();

    /**
     * Fund codes whose TEFAS profile (settlement valör, ISIN, KAP link, trade window, risk) needs (re)fetching:
     * never enriched, or last enriched before {@code staleBefore}. Keying on {@code profileEnrichedAt} (not the
     * old "isinCode IS NULL") means a fund whose profile legitimately has no ISIN still converges instead of being
     * re-fetched every cycle, and the profile is periodically refreshed so a changed valör/risk is picked up.
     *
     * @param staleBefore re-enrich anything enriched before this instant
     * @return persisted fund codes needing profile enrichment
     */
    @Query("SELECT f.fundCode FROM Fund f WHERE f.profileEnrichedAt IS NULL OR f.profileEnrichedAt < :staleBefore")
    List<String> findFundCodesNeedingProfile(@Param("staleBefore") LocalDateTime staleBefore);

    /**
     * Aggregates the number of funds per category for summary/breakdown views.
     *
     * <p>Funds with a {@code null} type are excluded; results are ordered by type.
     *
     * @return rows of {@code [FundType, Long count]}
     */
    @Query("SELECT f.fundType, COUNT(f) FROM Fund f WHERE f.fundType IS NOT NULL GROUP BY f.fundType ORDER BY f.fundType")
    List<Object[]> countByFundType();

    /**
     * Lists the distinct, non-null fund sub-categories in alphabetical order.
     *
     * <p>Used to populate filter facets for fund browsing.
     *
     * @return distinct sub-category labels, sorted ascending
     */
    @Query("SELECT DISTINCT f.subCategory FROM Fund f WHERE f.subCategory IS NOT NULL ORDER BY f.subCategory")
    List<String> findDistinctSubCategories();
}
