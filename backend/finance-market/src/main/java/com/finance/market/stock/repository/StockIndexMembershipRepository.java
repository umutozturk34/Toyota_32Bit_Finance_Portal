package com.finance.market.stock.repository;

import com.finance.market.stock.model.StockIndexMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Persistence for {@link StockIndexMembership}; supports per-stock lookup and reconcile (delete-stale). */
@Repository
public interface StockIndexMembershipRepository
        extends JpaRepository<StockIndexMembership, StockIndexMembership.Key> {

    /** All index memberships of one stock (e.g. the indices a stock belongs to, for its detail page). */
    List<StockIndexMembership> findByIdStockSymbol(String stockSymbol);

    /** All member stocks of one index, weight-descending (the reverse view: an index's constituents). */
    List<StockIndexMembership> findByIdIndexCodeOrderByWeightDesc(String indexCode);

    /** Removes every membership of a stock — used before re-inserting the freshly fetched set. */
    void deleteByIdStockSymbol(String stockSymbol);
}
