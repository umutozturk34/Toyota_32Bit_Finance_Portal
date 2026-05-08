package com.finance.portfolio.repository;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.common.model.TrackedAssetType;
import com.finance.portfolio.model.AssetType;
import java.math.BigDecimal;
import com.finance.portfolio.model.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    List<PortfolioPosition> findByPortfolioId(Long portfolioId);

    List<PortfolioPosition> findByPortfolioIdAndQuantityGreaterThan(
            Long portfolioId, BigDecimal minQuantity);

    List<PortfolioPosition> findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
            Long portfolioId, TrackedAssetType assetType, BigDecimal minQuantity);
}
