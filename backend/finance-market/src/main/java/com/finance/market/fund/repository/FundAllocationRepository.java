package com.finance.market.fund.repository;

import com.finance.market.fund.model.FundAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Persistence access for {@link FundAllocation} rows describing a fund's
 * portfolio composition (asset-class weightings).
 *
 * <p>Allocations are fully owned by their parent fund: refreshes replace the
 * existing set, hence the bulk delete-by-fund operations below.
 */
public interface FundAllocationRepository extends JpaRepository<FundAllocation, Long> {

    /**
     * Returns a fund's allocation breakdown ordered by descending weight.
     *
     * @param fundCode the owning fund's code
     * @return allocations largest-share first, empty if none recorded
     */
    List<FundAllocation> findByFundCodeOrderByPercentageDesc(String fundCode);

    /**
     * Bulk-deletes all allocations belonging to a single fund.
     *
     * <p>Used to clear stale composition before re-inserting a refreshed set.
     *
     * @param fundCode the owning fund's code
     */
    @Modifying
    @Query("DELETE FROM FundAllocation a WHERE a.fundCode = :fundCode")
    void deleteByFundCode(@Param("fundCode") String fundCode);

    /**
     * Bulk-deletes all allocations belonging to any of the given funds.
     *
     * <p>Batch variant of {@link #deleteByFundCode(String)} for multi-fund refresh.
     *
     * @param fundCodes the owning funds' codes
     */
    @Modifying
    @Query("DELETE FROM FundAllocation a WHERE a.fundCode IN :fundCodes")
    void deleteByFundCodeIn(@Param("fundCodes") Collection<String> fundCodes);
}
