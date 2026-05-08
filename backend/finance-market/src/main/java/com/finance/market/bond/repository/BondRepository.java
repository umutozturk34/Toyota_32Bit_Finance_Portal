package com.finance.market.bond.repository;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BondRepository extends JpaRepository<Bond, String>, JpaSpecificationExecutor<Bond> {

    List<Bond> findByBondType(BondType bondType);

    @Query("SELECT b.seriesCode FROM Bond b")
    List<String> findAllSeriesCodes();

    @Query("SELECT b.bondType, COUNT(b) FROM Bond b WHERE b.bondType IS NOT NULL GROUP BY b.bondType ORDER BY b.bondType")
    List<Object[]> countByBondType();
}
