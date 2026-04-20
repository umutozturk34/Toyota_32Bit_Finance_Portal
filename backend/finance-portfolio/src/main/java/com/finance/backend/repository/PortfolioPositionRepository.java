package com.finance.backend.repository;

import com.finance.backend.model.AssetType;
import java.math.BigDecimal;
import com.finance.backend.model.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    List<PortfolioPosition> findByPortfolioId(Long portfolioId);

    Optional<PortfolioPosition> findByPortfolioIdAndAssetTypeAndAssetCode(
            Long portfolioId, AssetType assetType, String assetCode);

    List<PortfolioPosition> findByPortfolioIdAndQuantityGreaterThan(
            Long portfolioId, BigDecimal minQuantity);

    List<PortfolioPosition> findByPortfolioIdAndAssetTypeAndQuantityGreaterThan(
            Long portfolioId, AssetType assetType, BigDecimal minQuantity);
}
