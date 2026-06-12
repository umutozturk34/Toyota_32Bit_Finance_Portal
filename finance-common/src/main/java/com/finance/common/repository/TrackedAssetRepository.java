package com.finance.common.repository;

import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link TrackedAsset} watchlist entries. Listing queries order by
 * {@code sortOrder} then code, and an enabled-only variant excludes soft-disabled entries.
 */
public interface TrackedAssetRepository extends JpaRepository<TrackedAsset, Long>,
        JpaSpecificationExecutor<TrackedAsset> {

    boolean existsByAssetType(TrackedAssetType assetType);

    List<TrackedAsset> findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType assetType);

    List<TrackedAsset> findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType assetType);

    Optional<TrackedAsset> findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType assetType, String assetCode);
}
