package com.finance.backend.repository;

import com.finance.backend.model.Fund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FundRepository extends JpaRepository<Fund, String> {
    List<Fund> findByFundType(String fundType);

    @Query("SELECT f.fundCode FROM Fund f")
    List<String> findAllFundCodes();
}
