package com.finance.backend.repository;

import com.finance.backend.model.TrackedAsset;
import com.finance.backend.model.TrackedAssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackedAssetRepository extends JpaRepository<TrackedAsset, Long>,
        JpaSpecificationExecutor<TrackedAsset> {

    boolean existsByAssetType(TrackedAssetType assetType);

    List<TrackedAsset> findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType assetType);

    List<TrackedAsset> findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType assetType);

    Optional<TrackedAsset> findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType assetType, String assetCode);
}
