package com.finance.market.bond.repository;

import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Persistence access for {@link Bond} entities, keyed by series code.
 *
 * <p>Extends {@link JpaSpecificationExecutor} so callers can build dynamic, filterable
 * bond queries (e.g. search/listing facets) without dedicated derived methods.
 */
public interface BondRepository extends JpaRepository<Bond, String>, JpaSpecificationExecutor<Bond> {

    /**
     * Returns all bonds belonging to the given category.
     *
     * @param bondType the bond category to filter by
     * @return bonds of the requested type, empty if none match
     */
    List<Bond> findByBondType(BondType bondType);

    /**
     * Projects only the series codes (primary keys) of all bonds.
     *
     * <p>Lightweight lookup used to drive batch refresh/iteration without loading
     * full entities.
     *
     * @return every persisted bond series code
     */
    @Query("SELECT b.seriesCode FROM Bond b")
    List<String> findAllSeriesCodes();

    /**
     * Aggregates the number of bonds per category for summary/breakdown views.
     *
     * <p>Bonds with a {@code null} type are excluded; results are ordered by type.
     *
     * @return rows of {@code [BondType, Long count]}
     */
    @Query("SELECT b.bondType, COUNT(b) FROM Bond b WHERE b.bondType IS NOT NULL GROUP BY b.bondType ORDER BY b.bondType")
    List<Object[]> countByBondType();
}
