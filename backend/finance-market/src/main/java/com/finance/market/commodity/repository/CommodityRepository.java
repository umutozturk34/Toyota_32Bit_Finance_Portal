package com.finance.market.commodity.repository;

import com.finance.market.commodity.model.Commodity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Persistence access for {@link Commodity} entities, keyed by commodity code.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic, filterable
 * commodity listing/search queries.
 */
public interface CommodityRepository extends JpaRepository<Commodity, String>, JpaSpecificationExecutor<Commodity> {

    /**
     * Returns all commodities ordered alphabetically by commodity code.
     *
     * @return commodities sorted ascending by code
     */
    List<Commodity> findAllByOrderByCommodityCodeAsc();

    /**
     * Aggregates the commodity count per market segment for breakdown views.
     *
     * <p>Commodities with a {@code null} segment are excluded.
     *
     * @return rows of {@code [segment, Long count]}
     */
    @Query("SELECT c.commoditySegment AS segment, COUNT(c) AS total FROM Commodity c " +
            "WHERE c.commoditySegment IS NOT NULL GROUP BY c.commoditySegment")
    List<Object[]> countBySegment();
}
