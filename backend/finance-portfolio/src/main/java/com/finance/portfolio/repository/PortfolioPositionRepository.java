package com.finance.portfolio.repository;

import com.finance.common.model.TrackedAssetType;
import com.finance.portfolio.model.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

/** Persistence for spot {@link PortfolioPosition} lots, with portfolio- and tracked-asset-scoped queries. */
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    List<PortfolioPosition> findByPortfolioId(Long portfolioId);

    long countByPortfolioId(Long portfolioId);

    List<PortfolioPosition> findByPortfolioIdAndQuantityGreaterThan(
            Long portfolioId, BigDecimal minQuantity);

    List<PortfolioPosition> findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
            Long portfolioId, TrackedAssetType assetType, BigDecimal minQuantity);

    boolean existsByPortfolioIdAndTrackedAsset_IdAndIdNot(
            Long portfolioId, Long trackedAssetId, Long excludedPositionId);

    void deleteByPortfolioId(Long portfolioId);
}
