package com.finance.backend.repository;

import com.finance.backend.model.Fund;
import com.finance.backend.model.FundType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FundRepository extends JpaRepository<Fund, String>, JpaSpecificationExecutor<Fund> {
    List<Fund> findByFundType(FundType fundType);

    @Query("SELECT f.fundCode FROM Fund f")
    List<String> findAllFundCodes();

    @Query("SELECT f.fundType, COUNT(f) FROM Fund f WHERE f.fundType IS NOT NULL GROUP BY f.fundType ORDER BY f.fundType")
    List<Object[]> countByFundType();
}
