package com.finance.common.repository;

import com.finance.common.model.Asset;
import com.finance.common.model.MarketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByMarketTypeAndAssetCodeIgnoreCase(MarketType marketType, String assetCode);

    boolean existsByMarketTypeAndAssetCodeIgnoreCase(MarketType marketType, String assetCode);

    List<Asset> findByMarketType(MarketType marketType);
}
