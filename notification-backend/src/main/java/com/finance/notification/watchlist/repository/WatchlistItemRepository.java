package com.finance.notification.watchlist.repository;

import com.finance.common.model.TrackedAssetType;
import com.finance.notification.watchlist.model.WatchlistItem;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link WatchlistItem}: per-list/per-user/per-market lookups, dedup by tracked
 * asset, item counts and the next display-order helper for appending items.
 */
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByUserSubOrderByCreatedAtDesc(String userSub);

    List<WatchlistItem> findByWatchlistId(Long watchlistId, Sort sort);

    List<WatchlistItem> findByTrackedAsset_AssetType(TrackedAssetType assetType);

    Optional<WatchlistItem> findByWatchlistIdAndTrackedAsset_Id(Long watchlistId, Long trackedAssetId);

    long countByWatchlistId(Long watchlistId);

    @Query("SELECT COALESCE(MAX(i.displayOrder), 0) FROM WatchlistItem i WHERE i.watchlistId = :watchlistId")
    int findMaxDisplayOrderByWatchlistId(Long watchlistId);
}
