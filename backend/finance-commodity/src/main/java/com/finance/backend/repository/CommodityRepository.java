package com.finance.backend.repository;

import com.finance.backend.model.Commodity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommodityRepository extends JpaRepository<Commodity, String>, JpaSpecificationExecutor<Commodity> {

    List<Commodity> findAllByOrderByCommodityCodeAsc();

    @Query("SELECT c.commoditySegment AS segment, COUNT(c) AS total FROM Commodity c " +
            "WHERE c.commoditySegment IS NOT NULL GROUP BY c.commoditySegment")
    List<Object[]> countBySegment();
}
