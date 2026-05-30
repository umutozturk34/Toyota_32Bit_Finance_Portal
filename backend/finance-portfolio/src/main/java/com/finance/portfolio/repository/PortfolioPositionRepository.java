package com.finance.portfolio.repository;

import com.finance.common.model.TrackedAssetType;
import com.finance.portfolio.model.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/** Persistence for spot {@link PortfolioPosition} lots, with portfolio- and tracked-asset-scoped queries. */
@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    List<PortfolioPosition> findByPortfolioId(Long portfolioId);

    /** Portfolios that own at least one lot but have no daily snapshot yet (e.g. SQL-seeded portfolios). */
    @Query("""
            SELECT DISTINCT p.portfolioId FROM PortfolioPosition p
            WHERE NOT EXISTS (
                SELECT 1 FROM PortfolioDailySnapshot s WHERE s.portfolioId = p.portfolioId)
            """)
    List<Long> findPortfolioIdsWithoutSnapshots();

    List<PortfolioPosition> findByPortfolioIdAndQuantityGreaterThan(
            Long portfolioId, BigDecimal minQuantity);

    List<PortfolioPosition> findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
            Long portfolioId, TrackedAssetType assetType, BigDecimal minQuantity);

    boolean existsByPortfolioIdAndTrackedAsset_IdAndIdNot(
            Long portfolioId, Long trackedAssetId, Long excludedPositionId);

    void deleteByPortfolioId(Long portfolioId);
}
