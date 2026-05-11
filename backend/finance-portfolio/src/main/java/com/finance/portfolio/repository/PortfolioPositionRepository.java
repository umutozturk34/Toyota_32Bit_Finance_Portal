package com.finance.portfolio.repository;
import com.finance.common.model.TrackedAssetType;

import java.math.BigDecimal;
import com.finance.portfolio.model.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    List<PortfolioPosition> findByPortfolioId(Long portfolioId);

    List<PortfolioPosition> findByPortfolioIdAndQuantityGreaterThan(
            Long portfolioId, BigDecimal minQuantity);

    List<PortfolioPosition> findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
            Long portfolioId, TrackedAssetType assetType, BigDecimal minQuantity);
}
