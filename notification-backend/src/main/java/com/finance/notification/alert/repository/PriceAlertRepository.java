package com.finance.notification.alert.repository;

import com.finance.common.model.TrackedAssetType;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.alert.model.PriceAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

/** Persistence for {@link PriceAlert}, including duplicate detection and per-user/per-market lookups. */
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    Page<PriceAlert> findByUserSubOrderByCreatedAtDesc(String userSub, Pageable pageable);

    List<PriceAlert> findByActiveTrueAndTrackedAsset_AssetType(TrackedAssetType assetType);

    /**
     * Keyset page (id strictly greater than {@code lastId}, ascending) of active, not-yet-triggered alerts for
     * the asset type. Keyset (not offset) paging is required because the evaluator deactivates fired alerts
     * mid-scan, which would shift an offset window and skip rows; advancing by id is immune to that.
     */
    List<PriceAlert> findByActiveTrueAndTrackedAsset_AssetTypeAndIdGreaterThan(
            TrackedAssetType assetType, Long lastId, Pageable pageable);

    long countByUserSub(String userSub);

    long countByUserSubAndTrackedAsset_Id(String userSub, Long trackedAssetId);

    boolean existsByUserSubAndTrackedAsset_IdAndDirectionAndThresholdAndActiveTrue(
            String userSub, Long trackedAssetId, AlertDirection direction, BigDecimal threshold);
}
