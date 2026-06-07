package com.finance.market.stock.repository;
import com.finance.market.stock.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence access for {@link Stock} entities, keyed by their symbol. Extends
 * {@link JpaSpecificationExecutor} to support dynamic, criteria-based stock filtering.
 */
@Repository
public interface StockRepository extends JpaRepository<Stock, String>, JpaSpecificationExecutor<Stock> {
    /**
     * Projects only the stock symbols, avoiding loading full entities when callers just need the
     * universe of tickers (e.g. to drive bulk price refreshes).
     */
    @Query("SELECT s.symbol FROM Stock s")
    List<String> findAllSymbols();

    /**
     * Aggregates the number of stocks per market segment, skipping rows with no segment.
     *
     * @return rows of {@code [segment, count]} ordered by segment
     */
    @Query("SELECT s.stockSegment, COUNT(s) FROM Stock s WHERE s.stockSegment IS NOT NULL GROUP BY s.stockSegment ORDER BY s.stockSegment")
    List<Object[]> countBySegment();
}
