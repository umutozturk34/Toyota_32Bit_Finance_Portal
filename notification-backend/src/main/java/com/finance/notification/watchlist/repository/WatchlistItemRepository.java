package com.finance.notification.watchlist.repository;

import com.finance.common.model.MarketType;
import com.finance.notification.watchlist.model.WatchlistItem;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByUserSubOrderByCreatedAtDesc(String userSub);

    List<WatchlistItem> findByWatchlistId(Long watchlistId, Sort sort);

    List<WatchlistItem> findByMarketType(MarketType marketType);

    Optional<WatchlistItem> findByWatchlistIdAndMarketTypeAndAssetCode(Long watchlistId,
                                                                       MarketType marketType,
                                                                       String assetCode);

    long countByWatchlistId(Long watchlistId);

    @Query("SELECT COALESCE(MAX(i.displayOrder), 0) FROM WatchlistItem i WHERE i.watchlistId = :watchlistId")
    int findMaxDisplayOrderByWatchlistId(Long watchlistId);
}
