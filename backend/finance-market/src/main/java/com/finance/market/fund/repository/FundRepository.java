package com.finance.market.fund.repository;

import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

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
