package com.finance.market.fund.repository;

import com.finance.market.fund.model.FundAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FundAllocationRepository extends JpaRepository<FundAllocation, Long> {

    List<FundAllocation> findByFundCodeOrderByPercentageDesc(String fundCode);

    @Modifying
    @Query("DELETE FROM FundAllocation a WHERE a.fundCode = :fundCode")
    void deleteByFundCode(@Param("fundCode") String fundCode);

    @Modifying
    @Query("DELETE FROM FundAllocation a WHERE a.fundCode IN :fundCodes")
    void deleteByFundCodeIn(@Param("fundCodes") Collection<String> fundCodes);
}
